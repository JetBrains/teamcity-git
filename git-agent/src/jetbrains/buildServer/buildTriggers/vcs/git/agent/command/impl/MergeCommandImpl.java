package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.MergeCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class MergeCommandImpl extends BaseCommandImpl implements MergeCommand {
  private final List<String> myMergeBranches = new ArrayList<>();
  private boolean myAbort = false;
  private boolean myQuite = false;

  public MergeCommandImpl(@NotNull GitCommandLine myCmd) {
    super(myCmd);
  }

  @NotNull
  @Override
  public MergeCommand setBranches(String... mergeBranches) {
    myMergeBranches.addAll(Arrays.asList(mergeBranches));
    return this;
  }

  @NotNull
  @Override
  public MergeCommand setAbort(boolean abort) {
    myAbort = abort;
    return this;
  }

  @NotNull
  public MergeCommand setQuite(boolean quite) {
    myQuite = quite;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("merge");

    if (myQuite) {
      cmd.addParameter("-q");
    }

    if (myAbort) {
      cmd.addParameters("--abort");
    }

    cmd.addParameters(myMergeBranches);

    ExecResult result = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, result);
  }
}
