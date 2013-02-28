package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleSyncCommand;
import jetbrains.buildServer.vcs.VcsException;

public class SubmoduleSyncCommandImpl implements SubmoduleSyncCommand {

  private final GitCommandLine myCmd;

  public SubmoduleSyncCommandImpl(GitCommandLine myCmd) {
    this.myCmd = myCmd;
  }

  public void call() throws VcsException {
    myCmd.addParameter("submodule");
    myCmd.addParameter("sync");
    CommandUtil.runCommand(myCmd);
  }
}
