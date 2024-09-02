

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface DeleteTagCommand extends BaseCommand {

  @NotNull
  DeleteTagCommand setName(@NotNull String tagFullName);

  void call() throws VcsException;

}