

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.RepackCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RepackCommandImpl extends BaseCommandImpl implements RepackCommand {

  public RepackCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("repack", "-a", "-d");
    CommandUtil.runCommand(cmd);
  }
}