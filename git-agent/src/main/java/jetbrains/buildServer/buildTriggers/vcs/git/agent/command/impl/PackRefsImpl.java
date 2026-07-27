

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.PackRefs;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class PackRefsImpl extends BaseCommandImpl implements PackRefs {
  private boolean myErrorExpected = false;

  public PackRefsImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public PackRefsImpl setErrorExpected(boolean expected) {
    myErrorExpected = expected;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.abnormalExitExpected(myErrorExpected);
    cmd.addParameters("pack-refs", "--all");
    CommandUtil.runCommand(cmd);
  }
}