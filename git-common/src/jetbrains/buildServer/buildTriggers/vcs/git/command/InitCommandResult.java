package jetbrains.buildServer.buildTriggers.vcs.git.command;

public class InitCommandResult {

  private final String myDefaultBranch;

  public InitCommandResult(String defaultBranch) {
    myDefaultBranch = defaultBranch;
  }

  public String getDefaultBranch() {
    return myDefaultBranch;
  }
}
