

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface AddRemoteCommand extends BaseCommand {

  @NotNull
  AddRemoteCommand setName(@NotNull String name);

  @NotNull
  AddRemoteCommand setUrl(@NotNull String url);

  void call() throws VcsException;

}