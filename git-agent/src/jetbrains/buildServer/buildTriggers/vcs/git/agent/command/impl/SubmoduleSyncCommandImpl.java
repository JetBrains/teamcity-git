

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleSyncCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SubmoduleSyncCommandImpl extends BaseCommandImpl implements SubmoduleSyncCommand {

  public SubmoduleSyncCommandImpl(@NotNull GitCommandLine myCmd) {
    super(myCmd);
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("submodule");
    cmd.addParameter("sync");
    CommandUtil.runCommand(cmd);
  }
}