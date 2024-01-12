

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.DeleteBranchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class DeleteBranchCommandImpl extends BaseCommandImpl implements DeleteBranchCommand {

  private String myName;

  public DeleteBranchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public DeleteBranchCommand setName(@NotNull String name) {
    myName = name;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("branch");
    cmd.addParameter("-D");
    cmd.addParameter(myName);
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}