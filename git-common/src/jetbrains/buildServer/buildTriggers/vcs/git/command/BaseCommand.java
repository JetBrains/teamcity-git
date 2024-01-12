

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import org.jetbrains.annotations.NotNull;

public interface BaseCommand {

  void addConfig(@NotNull String name, @NotNull String value);

  void setEnv(@NotNull String name, @NotNull String value);

  void addPostAction(@NotNull Runnable action);

  void throwExceptionOnNonZeroExitCode(boolean throwExceptionOnNonZeroExitCode);
}