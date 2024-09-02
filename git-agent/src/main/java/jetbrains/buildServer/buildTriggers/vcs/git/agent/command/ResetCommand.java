

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.AuthCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface ResetCommand extends BaseCommand, AuthCommand<ResetCommand> {

  @NotNull
  ResetCommand setHard(boolean doHard);

  @NotNull
  ResetCommand setRevision(@NotNull String revision);

  void call() throws VcsException;

}