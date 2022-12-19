package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.WrongCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * This command (git wrong) does not exist. It was created to determine which locale git uses by error message text
 */
public class WrongCommandImpl extends BaseCommandImpl implements WrongCommand {
  public WrongCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public String call() throws VcsException {
    GitCommandLine cmd = getCmd().stdErrExpected(true).abnormalExitExpected(true);
    cmd.addParameter("wrong-jb-test");
    return CommandUtil.runCommand(cmd, 60).getStderr();
  }
}
