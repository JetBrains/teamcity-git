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
  private final GitRepoOperations myRepoOperations;

  public GitCherryPickSupport(@NotNull GitVcsSupport vcs,
                              @NotNull CommitLoader commitLoader,
                              @NotNull RepositoryManager repositoryManager,
                              @NotNull GitRepoOperations repoOperations) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
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
  public CherryPickResult dryRunCherryPick(@NotNull VcsRoot root,
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
      return CherryPickResult.createRejected("No revisions to cherry-pick");

    String operation = publish ? "cherryPick" : "dryRunCherryPick";
    LOG.info(operation + " in root " + root + ", revisions " + srcRevisions + ", destination " + dstBranch);
    OperationContext context = myVcs.createContext(root, operation);
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        //the operation is not retried: its result is computed against the revision the destination branch pointed
        //to when it started, so publishing it after a concurrent update would mean publishing something the caller
        //never asked for
        return pickRevisions(context, gitRoot, context.getRepository(), srcRevisions, dstBranch, options, publish);
      } catch (CherryPickRejectedException e) {
        LOG.info(operation + " was rejected, root " + root + ", destination " + dstBranch + ": " + e.getMessage());
        return CherryPickResult.createRejected(e.getMessage());
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
                                         boolean publish) throws IOException, VcsException, CherryPickRejectedException {
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

      for (int i = 0; i < srcRevisions.size(); i++) {
        String srcRevision = srcRevisions.get(i);
        RevCommit original = walk.parseCommit(loadSourceCommit(context, gitRoot, db, srcRevision));

        boolean alreadyReachable = walk.isMergedInto(original, current);
        walk.reset(); //isMergedInto leaves the walk in a state unsuitable for further traversals
        if (alreadyReachable) {
          picked.add(CherryPickResult.PickedCommit.alreadyPresent(srcRevision));
          continue;
        }

        RevCommit base = getReplayBase(original, options);
        CommitReplay.Result replayed = CommitReplay.replay(db, original, current, base);
        if (replayed.isConflicted()) {
          LOG.info("Cherry-pick of " + original.name() + " into " + dstBranch + " failed with conflicts " + replayed.getConflicts());
          return CherryPickResult.createConflict("Unable to cherry-pick " + original.name() + " into " + dstBranch,
                                                 reportConflict(picked, srcRevisions, i, replayed.getConflicts()));
        }

        if (current.getTree().getId().equals(replayed.getTreeId())) {
          picked.add(CherryPickResult.PickedCommit.alreadyPresent(srcRevision));
          continue;
        }

        ObjectId created = createCommit(gitRoot, db, inserter, original, current, replayed.getTreeId());
        current = walk.parseCommit(created);
        anythingPicked = true;
        picked.add(publish ? CherryPickResult.PickedCommit.created(srcRevision, created.name())
                           : CherryPickResult.PickedCommit.pickable(srcRevision));
      }

      if (!anythingPicked)
        return CherryPickResult.createAlreadyPresent("All the requested changes are already present in " + dstBranch, picked);

      if (!publish)
        return CherryPickResult.createPickable(picked);

      push(gitRoot, db, dstRef, current, branchTip);
      LOG.info("Cherry-pick of " + srcRevisions + " into " + dstBranch + " successfully finished, new revision " + current.name());
      return CherryPickResult.createPicked(current.name(), picked);
    } finally {
      inserter.close();
      walk.close();
    }
  }

  /**
   * Nothing is published on a conflict, so the commits built before it are reported as pickable rather than as
   * created ones; the revisions after the conflicting one were not processed at all.
   *
   * @param processed what happened to the revisions before the conflicting one
   * @param conflictingIndex index of the conflicting revision in srcRevisions
   */
  @NotNull
  private static List<CherryPickResult.PickedCommit> reportConflict(@NotNull List<CherryPickResult.PickedCommit> processed,
                                                                   @NotNull List<String> srcRevisions,
                                                                   int conflictingIndex,
                                                                   @NotNull List<String> conflictFiles) {
    List<CherryPickResult.PickedCommit> result = new ArrayList<CherryPickResult.PickedCommit>(srcRevisions.size());
    for (CherryPickResult.PickedCommit commit : processed) {
      result.add(commit.getStatus() == CherryPickResult.PickedCommit.Status.ALREADY_PRESENT
                 ? commit
                 : CherryPickResult.PickedCommit.pickable(commit.getSourceRevision()));
    }
    result.add(CherryPickResult.PickedCommit.conflict(srcRevisions.get(conflictingIndex), conflictFiles));
    for (String notAttempted : srcRevisions.subList(conflictingIndex + 1, srcRevisions.size())) {
      result.add(CherryPickResult.PickedCommit.notAttempted(notAttempted));
    }
    return result;
  }

  private void push(@NotNull GitVcsRoot gitRoot,
                    @NotNull Repository db,
                    @NotNull String dstRef,
                    @NotNull RevCommit newTip,
                    @NotNull RevCommit expectedOldTip) throws VcsException, CherryPickRejectedException {
    ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
    lock.lock();
    try {
      myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString())
                      .push(db, gitRoot, dstRef, newTip.name(), expectedOldTip.name());
    } catch (VcsException e) {
      if (isUpdatedConcurrently(gitRoot, dstRef, expectedOldTip)) {
        LOG.info("Cherry-pick result was not published, " + dstRef + " was updated concurrently, root " + gitRoot, e);
        throw new CherryPickRejectedException("The '" + dstRef + "' destination branch was updated concurrently, " +
                                              "nothing was published");
      }
      //the push failed for a reason of its own, authentication or the network for instance: that is not a rejected
      //cherry-pick but a failure of the operation, so it must not be reported as a result
      throw e;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Loads the commit to pick, fetching it if it is missing in the mirror.
   *
   * @throws CherryPickRejectedException if the repository has no such revision; a fetch which fails for a reason of
   * its own reports a {@link VcsException}, because that is a failure of the operation and not its result
   */
  @NotNull
  private RevCommit loadSourceCommit(@NotNull OperationContext context,
                                     @NotNull GitVcsRoot gitRoot,
                                     @NotNull Repository db,
                                     @NotNull String revision) throws CherryPickRejectedException, VcsException, IOException {
    RevCommit commit = myCommitLoader.findCommit(db, revision);
    if (commit != null)
      return commit;
    try {
      return myCommitLoader.loadCommit(context, gitRoot, revision);
    } catch (RevisionNotFoundException e) {
      throw new CherryPickRejectedException("Cannot cherry-pick revision " + revision + ": " + e.getMessage());
    }
  }

  /**
   * Tells a lost compare-and-swap from a failure of the push itself: the result cannot be published when the
   * destination branch no longer points to the revision the operation was computed against.
   */
  private boolean isUpdatedConcurrently(@NotNull GitVcsRoot gitRoot, @NotNull String dstRef, @NotNull RevCommit expectedTip) {
    try {
      Ref current = myVcs.getRemoteRefs(gitRoot.getOriginalRoot()).get(dstRef);
      return current == null || current.getObjectId() == null || !expectedTip.name().equals(current.getObjectId().name());
    } catch (VcsException e) {
      LOG.debug("Cannot check whether " + dstRef + " was updated concurrently, root " + gitRoot, e);
      return false;
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
                                @NotNull ObjectId treeId) throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(treeId);
    cb.setParentId(parent);
    cb.setAuthor(GitServerUtil.getAuthorIdent(original));
    cb.setCommitter(PersonIdentFactory.getTagger(gitRoot, db));
    cb.setMessage(getCommitMessage(original));
    ObjectId commitId = inserter.insert(cb);
    inserter.flush();
    return commitId;
  }

  /**
   * @return message of the original commit, with the picked revision mentioned the way 'git cherry-pick -x' does it
   */
  @NotNull
  private String getCommitMessage(@NotNull RevCommit original) {
    String message = GitServerUtil.getFullMessage(original);
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

}
