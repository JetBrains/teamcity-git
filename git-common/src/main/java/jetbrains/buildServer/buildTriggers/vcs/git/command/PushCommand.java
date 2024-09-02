package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface PushCommand extends BaseCommand, AuthCommand<PushCommand> {

  @NotNull
  PushCommand setRefspec(@NotNull String refspec);

  @NotNull
  PushCommand setRemote(@NotNull String remoteUrl);

  void call() throws VcsException;
}
