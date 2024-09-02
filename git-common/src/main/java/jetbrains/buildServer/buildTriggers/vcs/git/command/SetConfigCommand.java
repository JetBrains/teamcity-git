package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface SetConfigCommand extends BaseCommand {
  @NotNull
  SetConfigCommand setPropertyName(@NotNull String name);

  @NotNull
  SetConfigCommand setValue(@NotNull String value);

  @NotNull
  SetConfigCommand unSet();

  void call() throws VcsException;
}
