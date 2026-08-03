package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

import static java.util.Arrays.asList;

public class GitCherryPickSupport implements CherryPickSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitCherryPickSupport.class.getName());

  /**
   * A line of the form 'Signed-off-by: ...' or '(cherry picked from commit ...)' at the end of a commit message,
   * the 'cherry picked from' line is appended to such a block without an empty line in between.
   */
  private static final Pattern TRAILER_LINE = Pattern.compile("(?:[A-Za-z][A-Za-z-]*: .*|\\(cherry picked from commit [0-9a-f]+\\))");

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final ServerPluginConfig myPluginConfig;
  private final GitRepoOperations myRepoOperations;

  public GitCherryPickSupport(@NotNull GitVcsSupport vcs,
                              @NotNull CommitLoader commitLoader,
                              @NotNull RepositoryManager repositoryManager,
                              @NotNull ServerPluginConfig pluginConfig,
                              @NotNull GitRepoOperations repoOperations) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myPluginConfig = pluginConfig;
    myRepoOperations = repoOperations;
    myVcs.addExtension(this);
  }

  @NotNull
  public CherryPickResult cherryPick(@NotNull VcsRoot root,
                                     @NotNull List<String> srcRevisions,
                                     @NotNull String dstBranch,
                                     @NotNull CherryPickOptions options) throws VcsException {
    return doCherryPick(root, srcRevisions, dstBranch, options, true);
  }

  @NotNull
  public CherryPickResult tryCherryPick(@NotNull VcsRoot root,
                                        @NotNull List<String> srcRevisions,
                                        @NotNull String dstBranch,
                                        @NotNull CherryPickOptions options) throws VcsException {
    return doCherryPick(root, srcRevisions, dstBranch, options, false);
  }

  @NotNull
  private CherryPickResult doCherryPick(@NotNull VcsRoot root,
                                        @NotNull List<String> srcRevisions,
                                        @NotNull String dstBranch,
                                        @NotNull CherryPickOptions options,
                                        boolean publish) throws VcsException {
    if (srcRevisions.isEmpty())
      return CherryPickResult.createError("No revisions to cherry-pick");

    String operation = publish ? "cherryPick" : "tryCherryPick";
    LOG.info(operation + " in root " + root + ", revisions " + srcRevisions + ", destination " + dstBranch);
    OperationContext context = myVcs.createContext(root, operation);
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        Repository db = context.getRepository();
        int attemptsLeft = Math.max(1, myPluginConfig.getMergeRetryAttempts());
        while (true) {
          try {
            return pickRevisions(context, gitRoot, db, srcRevisions, dstBranch, options, publish);
          } catch (CherryPickRejectedException e) {
            LOG.info(operation + " was rejected, root " + root + ", destination " + dstBranch + ": " + e.getMessage());
            return CherryPickResult.createError(e.getMessage());
          } catch (PushFailedException e) {
            attemptsLeft--;
            LOG.info("Failed to publish the cherry-pick result, root " + root + ", destination " + dstBranch +
                     ", attempts left " + attemptsLeft, e.getCause());
            if (attemptsLeft <= 0)
              return CherryPickResult.createError(e.getMessage());
          }
        }
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }

  @NotNull
  private CherryPickResult pickRevisions(@NotNull OperationContext context,
                                         @NotNull GitVcsRoot gitRoot,
                                         @NotNull Repository db,
                                         @NotNull List<String> srcRevisions,
                                         @NotNull String dstBranch,
                                         @NotNull CherryPickOptions options,
                                         boolean publish) throws IOException, VcsException, CherryPickRejectedException, PushFailedException {
    String dstRef = GitUtils.expandRef(dstBranch);
    Map<String, Ref> remoteRefs = myVcs.getRemoteRefs(gitRoot.getOriginalRoot());
    Ref remoteDstRef = remoteRefs.get(dstRef);
    if (remoteDstRef == null || remoteDstRef.getObjectId() == null)
      throw new CherryPickRejectedException("The '" + dstBranch + "' destination branch doesn't exist");

    RefSpec spec = new RefSpec().setSource(dstRef).setDestination(dstRef).setForceUpdate(true);
    myCommitLoader.fetch(db, gitRoot.getRepositoryFetchURL().get(), new FetchSettings(gitRoot.getAuthSettings(), asList(spec)));

    RevWalk walk = new RevWalk(db);
    ObjectInserter inserter = db.newObjectInserter();
    try {
      RevCommit branchTip = walk.parseCommit(myCommitLoader.loadCommit(context, gitRoot, remoteDstRef.getObjectId().name()));
      RevCommit current = branchTip;
      List<CherryPickResult.PickedCommit> picked = new ArrayList<CherryPickResult.PickedCommit>();
      boolean anythingPicked = false;

      for (String srcRevision : srcRevisions) {
        RevCommit original = walk.parseCommit(loadSourceCommit(context, gitRoot, db, srcRevision));

        boolean alreadyReachable = walk.isMergedInto(original, current);
        walk.reset(); //isMergedInto leaves the walk in a state unsuitable for further traversals
        if (alreadyReachable) {
          picked.add(CherryPickResult.PickedCommit.skipped(original.name(), "the commit is already reachable from " + dstBranch));
          continue;
        }

        RevCommit base = getReplayBase(original, options);
        CommitReplay.Result replayed = CommitReplay.replay(db, original, current, base);
        if (replayed.isConflicted()) {
          LOG.info("Cherry-pick of " + original.name() + " into " + dstBranch + " failed with conflicts " + replayed.getConflicts());
          return CherryPickResult.createConflict(original.name(), replayed.getConflicts(),
                                                 "Unable to cherry-pick " + original.name() + " into " + dstBranch);
        }

        if (current.getTree().getId().equals(replayed.getTreeId())) {
          picked.add(CherryPickResult.PickedCommit.skipped(original.name(), "the changes are already applied in " + dstBranch));
          continue;
        }

        ObjectId created = createCommit(gitRoot, db, inserter, original, current, replayed.getTreeId(), options);
        current = walk.parseCommit(created);
        anythingPicked = true;
        picked.add(publish ? CherryPickResult.PickedCommit.created(original.name(), created.name())
                           : CherryPickResult.PickedCommit.pickable(original.name()));
      }

      if (!anythingPicked) {
        return CherryPickResult.createNotPerformed("All the requested changes are already present in " + dstBranch,
                                                   publish ? branchTip.name() : null, picked);
      }

      if (!publish)
        return CherryPickResult.createSuccess(null, picked);

      push(gitRoot, db, dstRef, current, branchTip);
      LOG.info("Cherry-pick of " + srcRevisions + " into " + dstBranch + " successfully finished, new revision " + current.name());
      return CherryPickResult.createSuccess(current.name(), picked);
    } finally {
      inserter.close();
      walk.close();
    }
  }

  private void push(@NotNull GitVcsRoot gitRoot,
                    @NotNull Repository db,
                    @NotNull String dstRef,
                    @NotNull RevCommit newTip,
                    @NotNull RevCommit expectedOldTip) throws PushFailedException {
    ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
    lock.lock();
    try {
      myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString())
                      .push(db, gitRoot, dstRef, newTip.name(), expectedOldTip.name());
    } catch (VcsException e) {
      throw new PushFailedException(e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Loads the commit to pick, fetching it if it is missing in the mirror.
   */
  @NotNull
  private RevCommit loadSourceCommit(@NotNull OperationContext context,
                                     @NotNull GitVcsRoot gitRoot,
                                     @NotNull Repository db,
                                     @NotNull String revision) throws CherryPickRejectedException, IOException {
    RevCommit commit = myCommitLoader.findCommit(db, revision);
    if (commit != null)
      return commit;
    try {
      return myCommitLoader.loadCommit(context, gitRoot, revision);
    } catch (VcsException e) {
      throw new CherryPickRejectedException("Cannot cherry-pick revision " + revision + ": " + e.getMessage());
    }
  }

  /**
   * @return commit the changes of the picked commit should be computed against
   */
  @NotNull
  private RevCommit getReplayBase(@NotNull RevCommit original, @NotNull CherryPickOptions options) throws CherryPickRejectedException {
    int parentCount = original.getParentCount();
    if (parentCount == 0)
      throw new CherryPickRejectedException("Cannot cherry-pick the root commit " + original.name());
    if (parentCount == 1)
      return original.getParent(0);

    Integer mainlineParentNumber = options.getMainlineParentNumber();
    if (mainlineParentNumber == null) {
      throw new CherryPickRejectedException("Revision " + original.name() + " is a merge commit, " +
                                            "the number of the mainline parent must be specified to cherry-pick it");
    }
    if (mainlineParentNumber < 1 || mainlineParentNumber > parentCount) {
      throw new CherryPickRejectedException("Mainline parent number " + mainlineParentNumber + " is out of range, " +
                                            "revision " + original.name() + " has " + parentCount + " parents");
    }
    return original.getParent(mainlineParentNumber - 1);
  }

  @NotNull
  private ObjectId createCommit(@NotNull GitVcsRoot gitRoot,
                                @NotNull Repository db,
                                @NotNull ObjectInserter inserter,
                                @NotNull RevCommit original,
                                @NotNull RevCommit parent,
                                @NotNull ObjectId treeId,
                                @NotNull CherryPickOptions options) throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(treeId);
    cb.setParentId(parent);
    cb.setAuthor(GitServerUtil.getAuthorIdent(original));
    cb.setCommitter(getCommitter(gitRoot, db, options));
    cb.setMessage(getCommitMessage(original, options));
    ObjectId commitId = inserter.insert(cb);
    inserter.flush();
    return commitId;
  }

  @NotNull
  private PersonIdent getCommitter(@NotNull GitVcsRoot gitRoot, @NotNull Repository db, @NotNull CherryPickOptions options) {
    String committer = options.getCommitter();
    return committer != null ? PersonIdentFactory.parseIdent(committer) : PersonIdentFactory.getTagger(gitRoot, db);
  }

  /**
   * @return message of the original commit, with the picked revision mentioned the way 'git cherry-pick -x' does it
   */
  @NotNull
  private String getCommitMessage(@NotNull RevCommit original, @NotNull CherryPickOptions options) {
    String message = GitServerUtil.getFullMessage(original);
    if (!options.isAppendSourceRevision())
      return message;

    String sourceRevisionLine = "(cherry picked from commit " + original.name() + ")";
    if (message.contains(sourceRevisionLine))
      return message;

    StringBuilder result = new StringBuilder(trimTrailingWhitespace(message));
    if (result.length() > 0)
      result.append(endsWithTrailer(result.toString()) ? "\n" : "\n\n");
    return result.append(sourceRevisionLine).append("\n").toString();
  }

  private static boolean endsWithTrailer(@NotNull String message) {
    int lastLineStart = message.lastIndexOf('\n') + 1;
    return TRAILER_LINE.matcher(message.substring(lastLineStart)).matches();
  }

  @NotNull
  private static String trimTrailingWhitespace(@NotNull String message) {
    int end = message.length();
    while (end > 0 && Character.isWhitespace(message.charAt(end - 1))) {
      end--;
    }
    return message.substring(0, end);
  }

  /**
   * The cherry-pick cannot be performed, the reason is reported to the caller via {@link CherryPickResult#getError()}.
   */
  private static class CherryPickRejectedException extends Exception {
    private CherryPickRejectedException(@NotNull String message) {
      super(message);
    }
  }

  /**
   * Publishing the result failed, most probably because the destination branch was updated concurrently.
   */
  private static class PushFailedException extends Exception {
    private PushFailedException(@NotNull VcsException cause) {
      super(cause.getMessage(), cause);
    }
  }
}
