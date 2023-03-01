package jetbrains.buildServer.buildTriggers.vcs.git.command;

public class InitCommandResult {

  private final String myDefaultBranch;
  private final boolean myRepositoryExisted;

  public InitCommandResult(String defaultBranch, boolean repositoryExisted) {
    myDefaultBranch = defaultBranch;
    myRepositoryExisted = repositoryExisted;
  }

  public boolean isRepositoryExisted() {
    return myRepositoryExisted;
  }

  public String getDefaultBranch() {
    return myDefaultBranch;
  }
}
