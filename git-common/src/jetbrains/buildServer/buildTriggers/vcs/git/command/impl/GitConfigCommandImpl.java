package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitConfigCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitConfigCommandImpl extends BaseCommandImpl implements GitConfigCommand {
  private String myName;

  @Nullable
  private String myValue;

  private Scope myScope;

  public GitConfigCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }
  @NotNull
  public GitConfigCommand setPropertyName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  @Override
  public GitConfigCommand setValue(@Nullable String value) {
    myValue = value;
    return this;
  }

  @NotNull
  @Override
  public GitConfigCommand setScope(@NotNull Scope scope) {
    myScope = scope;
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
    cmd.addParameter("config");
    if (myScope != null) {
      cmd.addParameter(myScope.getKey());
    }
    cmd.addParameter(myName);
    if (myValue != null) {
      cmd.addParameter(myValue);
    }
    return CommandUtil.runCommand(cmd
                                    .abnormalExitExpected(abnormalExitExpected)
                                    .stdErrExpected(false))
                      .getStdout().trim();
  }
}
