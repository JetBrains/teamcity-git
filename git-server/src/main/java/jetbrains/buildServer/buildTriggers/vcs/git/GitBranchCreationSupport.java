package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.vcs.BranchCreationResult;
import jetbrains.buildServer.vcs.BranchCreationSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitBranchCreationSupport implements BranchCreationSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitBranchCreationSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final GitRepoOperations myRepoOperations;

  public GitBranchCreationSupport(@NotNull GitVcsSupport vcs,
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
  public BranchCreationResult createBranch(@NotNull VcsRoot root,
                                           @NotNull String branchName,
                                           @NotNull String revision) throws VcsException {
    LOG.info("Create branch " + branchName + " at revision " + revision + " in root " + root);
    if (!ObjectId.isId(revision))
      throw new VcsException("Cannot create branch " + branchName + ": '" + revision + "' is not a full revision");
    String ref = GitUtils.expandRef(branchName);
    if (!GitServerUtil.isBranch(ref)) {
      //a qualified ref is taken as is, and creating a tag or a note is not what this operation is for
      throw new VcsException("Cannot create branch " + branchName + ": '" + ref + "' is not a branch");
    }
    OperationContext context = myVcs.createContext(root, "createBranch");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        Repository db = context.getRepository();
        BranchCreationResult existing = getExistingBranch(gitRoot, ref, branchName, revision);
        if (existing != null) {
          LOG.info("Branch " + branchName + " already exists in root " + root + " at revision " + existing.getRevision());
          return existing;
        }

        RevCommit commit = myCommitLoader.findCommit(db, revision);
        if (commit == null)
          commit = myCommitLoader.loadCommit(context, gitRoot, revision);

        ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
        lock.lock();
        try {
          //the zero id means the ref must not exist yet, so a branch created meanwhile is reported instead of
          //being moved by this push
          myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString())
                          .push(db, gitRoot, ref, commit.name(), ObjectId.zeroId().name());
        } catch (VcsException e) {
          BranchCreationResult createdMeanwhile;
          try {
            createdMeanwhile = getExistingBranch(gitRoot, ref, branchName, revision);
          } catch (VcsException cannotTell) {
            //reading the branch failed too, so the reason of the push failure is the only thing left to report
            LOG.debug("Cannot read " + ref + " to find out why the push failed, root " + root, cannotTell);
            throw e;
          }
          if (createdMeanwhile != null) {
            LOG.info("Branch " + branchName + " was created concurrently in root " + root + " at revision " +
                     createdMeanwhile.getRevision(), e);
            return createdMeanwhile;
          }
          throw e;
        } finally {
          lock.unlock();
        }
        LOG.info("Branch " + branchName + " successfully created in root " + root + " at revision " + commit.name());
        return BranchCreationResult.created(branchName, commit.name());
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }

  /**
   * @return how the branch which is already there relates to the requested revision, null if there is no such
   * branch on the remote side
   */
  @Nullable
  private BranchCreationResult getExistingBranch(@NotNull GitVcsRoot gitRoot,
                                                 @NotNull String ref,
                                                 @NotNull String branchName,
                                                 @NotNull String revision) throws VcsException {
    Ref existingRef = myVcs.getRemoteRefs(gitRoot.getOriginalRoot()).get(ref);
    if (existingRef == null || existingRef.getObjectId() == null)
      return null;

    String existingRevision = existingRef.getObjectId().name();
    return existingRevision.equalsIgnoreCase(revision)
           ? BranchCreationResult.alreadyAtRevision(branchName, existingRevision)
           : BranchCreationResult.existsAtOtherRevision(branchName, existingRevision);
  }
}
