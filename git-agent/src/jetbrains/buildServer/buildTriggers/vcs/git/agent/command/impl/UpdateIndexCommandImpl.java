

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateIndexCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseAuthCommandImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class UpdateIndexCommandImpl extends BaseAuthCommandImpl<UpdateIndexCommand> implements UpdateIndexCommand {

  private boolean myReallyRefresh;
  private boolean myQuiet;

  public UpdateIndexCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }


  @NotNull
  @Override
  public UpdateIndexCommand reallyRefresh(final boolean reallyRefresh) {
    myReallyRefresh = reallyRefresh;
    return this;
  }


  @NotNull
  @Override
  public UpdateIndexCommand quiet(final boolean quiet) {
    myQuiet = quiet;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("update-index");
    if (myQuiet)
      cmd.addParameter("-q");
    if (myReallyRefresh)
      cmd.addParameter("--really-refresh");
    runCmd(cmd);
  }
}