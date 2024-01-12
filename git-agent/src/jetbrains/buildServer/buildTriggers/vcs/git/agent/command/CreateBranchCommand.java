

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface CreateBranchCommand extends BaseCommand {

  @NotNull
  CreateBranchCommand setName(@NotNull String name);

  @NotNull
  CreateBranchCommand setStartPoint(@NotNull String startPoint);

  @NotNull
  CreateBranchCommand setTrack(boolean track);


  void call() throws VcsException;

}