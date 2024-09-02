

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.AuthCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface CheckoutCommand extends BaseCommand, AuthCommand<CheckoutCommand> {

  @NotNull
  CheckoutCommand setForce(boolean force);

  @NotNull
  CheckoutCommand setBranch(@NotNull String branch);

  @NotNull
  CheckoutCommand setQuiet(boolean quiet);

  void call() throws VcsException;

}