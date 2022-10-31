package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface GetConfigCommand extends BaseCommand {
  @NotNull
  GetConfigCommand setPropertyName(@NotNull String name);

  @NotNull String call() throws VcsException;

  @NotNull
  String callWithIgnoreExitCode() throws VcsException;
}
