package jetbrains.buildServer.buildTriggers.vcs.git.agent.errors;

import jetbrains.buildServer.vcs.VcsException;

public class GitExecTimeout extends VcsException {
  public GitExecTimeout() {
    super("Timeout exception");
  }
}
