package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class InternalGitBranchSupport {
  private final TransportFactory myTransportFactory;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final ServerPluginConfig myPluginConfig;

  public InternalGitBranchSupport(@NotNull TransportFactory transportFactory,
                                  @NotNull CommitLoader commitLoader,
                                  @NotNull RepositoryManager repositoryManager,
                                  @NotNull ServerPluginConfig pluginConfig) {
    myTransportFactory = transportFactory;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myPluginConfig = pluginConfig;
  }

  public void createBranch(@NotNull GitVcsRoot gitRoot,
                           @NotNull Git git,
                           @NotNull Repository db,
                           @NotNull OperationContext context,
                           @NotNull String srcBranch,
                           @NotNull String newBranchName) throws IOException {
    try {
      fetchIfRequired(srcBranch, git, db, gitRoot);

      git.branchCreate()
         .setName(newBranchName)
         .setStartPoint(srcBranch)
         .call();

      pushNewBranch(newBranchName, context, db, git, gitRoot);

    } catch (GitAPIException | VcsException gitEx) {
      Loggers.VCS.warnAndDebugDetails("creating error", gitEx);
    }
  }

  @NotNull
  public String constructName(String srcBranch, String dstBranch) {
    return PreliminaryMergeManager.PRELIMINARY_MERGE_BRANCH_PREFIX + "/" + getLogicalName(srcBranch) + "/to/" + getLogicalName(dstBranch);
  }

  @NotNull
  private String getLogicalName(@NotNull String branchName) {
    if (branchName.startsWith("refs/heads/")) {
      return branchName.substring("refs/heads/".length());
    }
    return branchName;
  }

  private void fetchIfRequired(String branchName, Git git, Repository db, GitVcsRoot gitRoot) throws GitAPIException, IOException, VcsException {
    if (branchLastCommit(branchName, git, db) == null) {
      fetch(branchName, db, gitRoot);
    }
  }

  public void fetch(String branchName, Repository db, GitVcsRoot gitRoot) throws VcsException, IOException {
    RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(branchName)).setDestination(GitUtils.expandRef(branchName)).setForceUpdate(true);
    myCommitLoader.fetch(db, gitRoot.getRepositoryFetchURL().get(), asList(spec), new FetchSettings(gitRoot.getAuthSettings()));
  }

  private void pushNewBranch(String newBranchName, OperationContext context, Repository db, Git git, GitVcsRoot gitRoot) throws VcsException, IOException, GitAPIException {
    ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
    lock.lock();

    try {
      try (final Transport tn = myTransportFactory.createTransport(db, gitRoot.getRepositoryPushURL().get(), gitRoot.getAuthSettings(),
                                                                   myPluginConfig.getPushTimeoutSeconds())) {
        String topNewBranchCommitRevision = branchLastCommit(newBranchName, git, db);
        if (topNewBranchCommitRevision == null) {
          PreliminaryMergeManager.printToLogs("New branch was not created");
          return;
        }

        ObjectId commitId = myCommitLoader.loadCommit(context, gitRoot, topNewBranchCommitRevision);

        RemoteRefUpdate ru = new RemoteRefUpdate(db,
                                                 null,
                                                 commitId,
                                                 GitUtils.expandRef(newBranchName),
                                                 false,
                                                 GitUtils.expandRef(newBranchName),
                                                 null);

        tn.push(NullProgressMonitor.INSTANCE, Collections.singletonList(ru));

        switch (ru.getStatus()) {
          case UP_TO_DATE:
          case OK:
            PreliminaryMergeManager.printToLogs("New branch " + newBranchName + " was created and pushed");
          default:
            PreliminaryMergeManager.printToLogs("Warning! New branch " + newBranchName + " was created, but not pushed");
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Nullable
  public String branchLastCommit(String branchName, Git git, Repository db)  {
    try {
      ObjectId commitObject = db.resolve(branchName);
      if (commitObject == null)
        return null;
      return git.log().add(commitObject).call().iterator().next().getName();
    }  catch (GitAPIException | IOException gitEx) {
      Loggers.VCS.warnAndDebugDetails("getting branch Last commit error", gitEx);
      return null;
    }
  }

  public boolean isBranchTopCommitInTree(String branchName, Git git, Repository db, String targetBranchName) {
    try {
      String commitSHA = git.log().add(db.resolve(targetBranchName)).call().iterator().next().getName();

      int visitsLeft = 100;
      for (RevCommit rev : git.log().add(db.resolve(branchName)).call()) {
        if (visitsLeft-- == 0) {
          break;
        }

        if (rev.getName().equals(commitSHA)) {
          return true;
        }
      }

      return false;
    }  catch (GitAPIException | IOException gitEx) {
      Loggers.VCS.warnAndDebugDetails("finding branch top commit int tree error", gitEx);
      return false;
    }
  }
}
