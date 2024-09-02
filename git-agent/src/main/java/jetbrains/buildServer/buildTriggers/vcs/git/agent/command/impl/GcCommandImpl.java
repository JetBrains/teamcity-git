

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.GcCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GcCommandImpl extends BaseCommandImpl implements GcCommand {
  public GcCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("gc");
    CommandUtil.runCommand(cmd);
  }
}