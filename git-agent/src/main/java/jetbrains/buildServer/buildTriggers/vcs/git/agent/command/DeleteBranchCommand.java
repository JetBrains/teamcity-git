

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface DeleteBranchCommand extends BaseCommand {

  @NotNull
  DeleteBranchCommand setName(@NotNull String name);

  void call() throws VcsException;

}