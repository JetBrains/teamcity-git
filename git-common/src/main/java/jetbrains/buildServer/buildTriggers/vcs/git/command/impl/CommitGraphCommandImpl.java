package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.CommitGraphCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CommitGraphCommandImpl extends BaseCommandImpl implements CommitGraphCommand  {
  private String myCommandMode = "verify";
  private String myStrategy = null;
  private boolean myReachable = false;

  public CommitGraphCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public CommitGraphCommand setVerifyCommand() {
    myCommandMode = "verify";
    return this;
  }

  @NotNull
  @Override
  public CommitGraphCommand setWriteCommand() {
    myCommandMode = "write";
    return this;
  }

  @NotNull
  @Override
  public CommitGraphCommand setStrategy(@NotNull String strategy) {
    myStrategy = strategy;
    return this;
  }

  @NotNull
  @Override
  public CommitGraphCommand setReachable() {
    myReachable = true;
    return this;
  }

  @Override
  public int call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("commit-graph", myCommandMode);

    if (myReachable) {
      cmd.addParameter("--reachable");
    }

    if (myStrategy != null) {
      cmd.addParameter("--split=" + myStrategy);
    }

    ExecResult result = CommandUtil.runCommand(cmd.abnormalExitExpected(true).stdErrExpected(true));
    return result.getExitCode();
  }
}
