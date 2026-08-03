package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
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
    String ref = GitUtils.expandRef(branchName);
    OperationContext context = myVcs.createContext(root, "createBranch");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        Map<String, Ref> remoteRefs = myVcs.getRemoteRefs(gitRoot.getOriginalRoot());
        Ref existingRef = remoteRefs.get(ref);
        if (existingRef != null && existingRef.getObjectId() != null) {
          LOG.info("Branch " + branchName + " already exists in root " + root + " at revision " + existingRef.getObjectId().name());
          return BranchCreationResult.alreadyExists(branchName, existingRef.getObjectId().name());
        }

        Repository db = context.getRepository();
        RevCommit commit = myCommitLoader.findCommit(db, revision);
        if (commit == null)
          commit = myCommitLoader.loadCommit(context, gitRoot, revision);

        ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
        lock.lock();
        try {
          //the zero id means the ref must not exist yet, so a concurrent creation fails instead of being overwritten
          myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString())
                          .push(db, gitRoot, ref, commit.name(), ObjectId.zeroId().name());
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
}
