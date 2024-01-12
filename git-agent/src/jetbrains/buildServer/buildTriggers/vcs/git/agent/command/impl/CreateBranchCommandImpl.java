

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CreateBranchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CreateBranchCommandImpl extends BaseCommandImpl implements CreateBranchCommand {

  private String myName;
  private String myStartPoint;
  private boolean myTrack = false;

  public CreateBranchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public CreateBranchCommand setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public CreateBranchCommand setStartPoint(@NotNull String startPoint) {
    myStartPoint = startPoint;
    return this;
  }

  @NotNull
  public CreateBranchCommand setTrack(boolean track) {
    myTrack = track;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("branch");
    cmd.addParameter("--create-reflog");
    if (myTrack) {
      cmd.addParameter("--track");
    } else {
      cmd.addParameter("--no-track");
    }
    cmd.addParameter(myName);
    if (myStartPoint != null) {
      cmd.addParameter(myStartPoint);
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}