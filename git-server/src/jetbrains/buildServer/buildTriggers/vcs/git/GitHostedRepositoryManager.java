package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.impl.configsRepo.SelfHostedRepositoryManager;
import jetbrains.buildServer.vcs.VcsException;

public class GitHostedRepositoryManager implements SelfHostedRepositoryManager {

  private final GitVcsSupport myVcs;
  private final GitRepoOperations myGitRepoOperations;

  public GitHostedRepositoryManager(GitVcsSupport vcs, GitRepoOperations gitRepoOperations) {
    myVcs = vcs;
    myGitRepoOperations = gitRepoOperations;
  }

  @Override
  public void optimize(String repositoryPath) {
    try {
      myGitRepoOperations.repackCommand().repack(repositoryPath);
    } catch (VcsException e) {
      throw new RuntimeException("Unable to perform optimization for repository at path: " + repositoryPath);
    }
  }
}
