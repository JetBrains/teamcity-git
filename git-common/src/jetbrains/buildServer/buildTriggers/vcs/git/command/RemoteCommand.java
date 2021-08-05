package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface RemoteCommand extends BaseCommand, AuthCommand<RemoteCommand> {
  @NotNull
  RemoteCommand setRemote(@NotNull String name);

  @NotNull
  RemoteCommand setCommand(@NotNull String command);


  void call() throws VcsException;
}
