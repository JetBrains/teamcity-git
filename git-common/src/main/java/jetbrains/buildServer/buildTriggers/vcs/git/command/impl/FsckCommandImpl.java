package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FsckCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class FsckCommandImpl extends BaseCommandImpl implements FsckCommand {
  private boolean myConnectivityOnly = false;

  public FsckCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public FsckCommand setConnectivityOnly() {
    myConnectivityOnly = true;
    return this;
  }

  @Override
  public int call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("fsck");

    if (myConnectivityOnly) {
      cmd.addParameter("--connectivity-only");
    }

    ExecResult result = CommandUtil.runCommand(cmd.abnormalExitExpected(true).stdErrExpected(true));
    return result.getExitCode();
  }
}
