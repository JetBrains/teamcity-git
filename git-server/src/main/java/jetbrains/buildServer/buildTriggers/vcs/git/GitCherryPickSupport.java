package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class GitCherryPickSupport implements CherryPickSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitCherryPickSupport.class.getName());

  /**
   * A line of the form 'Signed-off-by: ...' or '(cherry picked from commit ...)', see {@link #endsWithTrailerBlock}.
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
    if (!GitServerUtil.isBranch(dstRef)) {
      //a qualified ref is taken as is, and a tag or a note is not something this operation may update
      throw new CherryPickRejectedException("The '" + dstBranch + "' destination is not a branch");
    }
    //the revisions to pick are loaded before anything is done to the destination branch: an operation which cannot
    //be performed anyway should not fetch it, and the loader brings the whole set in one go
    loadSourceCommits(context, gitRoot, db, srcRevisions);

    Map<String, Ref> remoteRefs = myVcs.getRemoteRefs(gitRoot.getOriginalRoot());
    Ref remoteDstRef = remoteRefs.get(dstRef);
    if (remoteDstRef == null || remoteDstRef.getObjectId() == null)
      throw new CherryPickRejectedException("The '" + dstBranch + "' destination branch doesn't exist");

    String observedRevision = remoteDstRef.getObjectId().name();
    fetchDestinationBranch(gitRoot, db, dstBranch, dstRef, observedRevision);

    try (RevWalk walk = new RevWalk(db); ObjectInserter inserter = db.newObjectInserter()) {
      RevCommit branchTip = walk.parseCommit(loadBranchTip(context, gitRoot, dstBranch, observedRevision));
      RevCommit current = branchTip;
      List<CherryPickResult.PickedCommit> picked = new ArrayList<CherryPickResult.PickedCommit>();
      boolean anythingPicked = false;

      for (int i = 0; i < srcRevisions.size(); i++) {
        String srcRevision = srcRevisions.get(i);
        RevCommit original = walk.parseCommit(ObjectId.fromString(srcRevision));

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
    }
  }

  /**
   * Brings the destination branch into the mirror, telling a concurrent update of it from a failure of the fetch
   * itself: the branch can be deleted or rewound between the moment it was read and the fetch, and the fetch of a
   * ref which is no longer there fails.
   *
   * @param observedRevision revision the branch pointed to when it was read
   */
  private void fetchDestinationBranch(@NotNull GitVcsRoot gitRoot,
                                      @NotNull Repository db,
                                      @NotNull String dstBranch,
                                      @NotNull String dstRef,
                                      @NotNull String observedRevision) throws IOException, VcsException, CherryPickRejectedException {
    RefSpec spec = new RefSpec().setSource(dstRef).setDestination(dstRef).setForceUpdate(true);
    try {
      myCommitLoader.fetch(db, gitRoot.getRepositoryFetchURL().get(), new FetchSettings(gitRoot.getAuthSettings(), asList(spec)));
    } catch (IOException | VcsException e) {
      String current;
      try {
        current = getRemoteRevision(gitRoot, dstRef);
      } catch (VcsException cannotTell) {
        LOG.debug("Cannot read " + dstRef + " to find out why the fetch failed, root " + gitRoot, cannotTell);
        throw e;
      }
      if (!observedRevision.equals(current)) {
        LOG.info("Cherry-pick was not started, " + dstRef + " was updated concurrently, root " + gitRoot, e);
        throw updatedConcurrently(dstBranch);
      }
      throw e;
    }
  }

  /**
   * Loads the revision the destination branch pointed to when the operation started.
   *
   * @throws CherryPickRejectedException if that revision is already gone, which means the branch was updated
   * between the moment it was read and the fetch: the result would be computed against a revision nobody has
   */
  @NotNull
  private RevCommit loadBranchTip(@NotNull OperationContext context,
                                  @NotNull GitVcsRoot gitRoot,
                                  @NotNull String dstBranch,
                                  @NotNull String revision) throws VcsException, IOException, CherryPickRejectedException {
    try {
      return myCommitLoader.loadCommit(context, gitRoot, revision);
    } catch (RevisionNotFoundException e) {
      throw updatedConcurrently(dstBranch);
    }
  }

  @NotNull
  private static CherryPickRejectedException updatedConcurrently(@NotNull String dstBranch) {
    return new CherryPickRejectedException("The '" + dstBranch + "' destination branch was updated concurrently, " +
                                          "nothing was published");
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
      String published;
      try {
        published = getRemoteRevision(gitRoot, dstRef);
      } catch (VcsException cannotTell) {
        LOG.debug("Cannot read " + dstRef + " to find out why the push failed, root " + gitRoot, cannotTell);
        throw e;
      }

      if (newTip.name().equals(published)) {
        //the remote accepted the update and the answer was lost on the way back: the result is published, and
        //reporting a failure would make the caller pick the same revisions once again
        LOG.warn("The push reported a failure, but " + dstRef + " points to the cherry-pick result " + newTip.name() +
                 ", root " + gitRoot, e);
        return;
      }
      if (!expectedOldTip.name().equals(published)) {
        LOG.info("Cherry-pick result was not published, " + dstRef + " was updated concurrently, root " + gitRoot, e);
        throw updatedConcurrently(dstRef);
      }
      //the branch is where it was and the push failed for a reason of its own, authentication or the network for
      //instance: that is not a rejected cherry-pick but a failure of the operation
      throw e;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Brings every revision to pick into the mirror: the loader checks what is missing first, so a set which is
   * already there costs nothing, and what is missing is fetched in one go rather than once per revision.
   *
   * @throws CherryPickRejectedException if a revision is malformed or the repository has no such revision; a fetch
   * which fails for a reason of its own reports a {@link VcsException}, because that is a failure of the operation
   * and not its result
   */
  private void loadSourceCommits(@NotNull OperationContext context,
                                 @NotNull GitVcsRoot gitRoot,
                                 @NotNull Repository db,
                                 @NotNull List<String> srcRevisions) throws CherryPickRejectedException, VcsException, IOException {
    List<RefCommit> toLoad = new ArrayList<RefCommit>(srcRevisions.size());
    for (String revision : srcRevisions) {
      if (!ObjectId.isId(revision)) {
        //an abbreviated or a malformed revision is not something the repository can be asked about, and the
        //contract reports a revision which cannot be picked as a result rather than as a failure of the operation
        throw new CherryPickRejectedException("Cannot cherry-pick revision " + revision + ": it is not a full revision");
      }
      //the revisions to pick are commits, not branch tips; the ref only tells the loader where to look for them
      //first, and the VCS root's own branch is where they are expected to be
      toLoad.add(sourceRevision(gitRoot.getRef(), revision));
    }

    try {
      myCommitLoader.loadCommits(context, gitRoot.getRepositoryFetchURL().get(), toLoad, remoteRefNames(gitRoot));
    } catch (RevisionNotFoundException e) {
      throw new CherryPickRejectedException("Cannot cherry-pick: " + e.getMessage());
    }

    for (String revision : srcRevisions) {
      if (myCommitLoader.findCommit(db, revision) == null)
        throw new CherryPickRejectedException("Cannot cherry-pick revision " + revision + ": it is not in the repository");
    }
  }

  @NotNull
  private static RefCommit sourceRevision(@NotNull String ref, @NotNull String revision) {
    return new RefCommit() {
      @NotNull
      @Override
      public String getRef() {
        return GitUtils.expandRef(ref);
      }

      @NotNull
      @Override
      public String getCommit() {
        return revision;
      }

      @Override
      public boolean isRefTip() {
        return false;
      }
    };
  }

  /**
   * @return names of the remote refs, read only if the loader has to fetch something
   */
  @NotNull
  private Supplier<Set<String>> remoteRefNames(@NotNull GitVcsRoot gitRoot) {
    return () -> {
      try {
        return myVcs.getRemoteRefs(gitRoot.getOriginalRoot()).keySet().stream()
                    .filter(r -> r.startsWith("refs/")).collect(Collectors.toSet());
      } catch (VcsException e) {
        throw new RuntimeException("Failed to read remote refs of " + gitRoot, e);
      }
    };
  }

  /**
   * @return revision the branch points to on the remote side, null if there is no such branch
   */
  @Nullable
  private String getRemoteRevision(@NotNull GitVcsRoot gitRoot, @NotNull String ref) throws VcsException {
    Ref remoteRef = myVcs.getRemoteRefs(gitRoot.getOriginalRoot()).get(ref);
    return remoteRef == null || remoteRef.getObjectId() == null ? null : remoteRef.getObjectId().name();
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
    String message = trimTrailingWhitespace(GitServerUtil.getFullMessage(original));
    StringBuilder result = new StringBuilder(message);
    if (!message.isEmpty())
      result.append(endsWithTrailerBlock(message) ? "\n" : "\n\n");
    return result.append("(cherry picked from commit ").append(original.name()).append(")\n").toString();
  }

  /**
   * @return true if the last paragraph of the message consists of trailer lines only: that is the block
   * 'git cherry-pick -x' appends its line to without an empty line in between. A message of a single paragraph is
   * the subject and its body, so the line is separated from it even when its last line looks like a trailer
   */
  private static boolean endsWithTrailerBlock(@NotNull String message) {
    int blockStart = message.lastIndexOf("\n\n");
    if (blockStart < 0)
      return false;
    for (String line : message.substring(blockStart + 2).split("\n")) {
      if (!TRAILER_LINE.matcher(line).matches())
        return false;
    }
    return true;
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
   * The cherry-pick cannot be performed, the reason is reported to the caller via {@link CherryPickResult#getMessage()}.
   */
  private static class CherryPickRejectedException extends Exception {
    private CherryPickRejectedException(@NotNull String message) {
      super(message);
    }
  }

}
