

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.AuthCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface SubmoduleUpdateCommand extends BaseCommand, AuthCommand<SubmoduleUpdateCommand> {

  @NotNull
  SubmoduleUpdateCommand setForce(boolean force);

  @NotNull
  SubmoduleUpdateCommand setDepth(int depth);

  void call() throws VcsException;
}