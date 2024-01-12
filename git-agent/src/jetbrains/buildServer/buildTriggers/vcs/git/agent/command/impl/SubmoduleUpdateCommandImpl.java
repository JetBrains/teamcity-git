

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseAuthCommandImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SubmoduleUpdateCommandImpl extends BaseAuthCommandImpl<SubmoduleUpdateCommand> implements SubmoduleUpdateCommand {

  private boolean myForce;
  private Integer myDepth;

  public SubmoduleUpdateCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public SubmoduleUpdateCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  @Override
  public SubmoduleUpdateCommand setDepth(final int depth) {
    myDepth = depth;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("submodule");
    cmd.addParameter("update");
    if (myForce)
      cmd.addParameter("--force");
    if (myDepth != null) {
      cmd.addParameter("--depth=" + myDepth);
    }
    runCmd(cmd);
  }
}