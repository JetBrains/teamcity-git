package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GetConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GetConfigCommandImpl extends BaseCommandImpl implements GetConfigCommand {
  private String myName;

  public GetConfigCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }
  @NotNull
  public GetConfigCommand setPropertyName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public String call() throws VcsException {
    return callWithLevel(false);
  }

  @NotNull
  public String callWithIgnoreExitCode() throws VcsException {
    return callWithLevel(true);
  }

  private String callWithLevel(boolean abnormalExitExpected) throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("config", myName);
    return CommandUtil.runCommand(cmd
                                    .abnormalExitExpected(abnormalExitExpected)
                                    .stdErrExpected(false))
                      .getStdout().trim();
  }
}
