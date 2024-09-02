

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LogCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LogCommandImpl extends BaseCommandImpl implements LogCommand {

  private String myStartPoint;
  private int myCommitsNumber;
  private String myFormat;

  public LogCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public LogCommand setStartPoint(@NotNull String startPoint) {
    myStartPoint = startPoint;
    return this;
  }

  @NotNull
  public LogCommand setCommitsNumber(int commitsNumber) {
    myCommitsNumber = commitsNumber;
    return this;
  }

  @NotNull
  public LogCommand setPrettyFormat(@NotNull String format) {
    myFormat = format;
    return this;
  }

  @Nullable
  public String call() {
    try {
      GitCommandLine cmd = getCmd();
      cmd.addParameters("log");
      if (myCommitsNumber != 0)
        cmd.addParameter("-n" + myCommitsNumber);
      if (myFormat != null)
        cmd.addParameter("--pretty=format:" + myFormat);
      cmd.addParameter(myStartPoint);
      cmd.addParameter("--");
      return CommandUtil.runCommand(cmd.stdErrExpected(false)).getStdout().trim();
    } catch (VcsException e) {
      return null;
    }
  }
}