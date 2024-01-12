

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ResetCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseAuthCommandImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class ResetCommandImpl extends BaseAuthCommandImpl<ResetCommand> implements ResetCommand {
  private boolean myHard = false;
  private String myRevision;

  public ResetCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public ResetCommand setHard(boolean doHard) {
    myHard = doHard;
    return this;
  }

  @NotNull
  public ResetCommand setRevision(@NotNull String revision) {
    myRevision = revision;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("reset");
    if (myHard)
      cmd.addParameter("--hard");
    cmd.addParameter(myRevision);
    runCmd(cmd);
  }
}