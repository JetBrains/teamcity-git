

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CheckoutCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseAuthCommandImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CheckoutCommandImpl extends BaseAuthCommandImpl<CheckoutCommand> implements CheckoutCommand {

  private boolean myForce;
  private String myBranch;
  private boolean myQuiet = true;

  public CheckoutCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public CheckoutCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  public CheckoutCommand setBranch(@NotNull String branch) {
    myBranch = branch;
    return this;
  }

  @NotNull
  @Override
  public CheckoutCommand setQuiet(boolean quiet) {
    myQuiet = quiet;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("checkout");
    if (myQuiet)
      cmd.addParameter("-q");
    if (myForce)
      cmd.addParameter("-f");
    cmd.addParameter(myBranch);
    runCmd(cmd);
  }
}