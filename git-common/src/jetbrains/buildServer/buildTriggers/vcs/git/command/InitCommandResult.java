package jetbrains.buildServer.buildTriggers.vcs.git.command;

import org.jetbrains.annotations.Nullable;

public class InitCommandResult {

  private final String myDefaultBranch;
  private final boolean myRepositoryExisted;

  public InitCommandResult(String defaultBranch, boolean repositoryExisted) {
    myDefaultBranch = defaultBranch;
    myRepositoryExisted = repositoryExisted;
  }

  public boolean repositoryAlreadyExists() {
    return myRepositoryExisted;
  }

  @Nullable
  public String getDefaultBranch() {
    return myDefaultBranch;
  }
}
