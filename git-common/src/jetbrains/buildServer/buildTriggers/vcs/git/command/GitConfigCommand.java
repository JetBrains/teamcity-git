package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GitConfigCommand extends BaseCommand {
  @NotNull
  GitConfigCommand setPropertyName(@NotNull String name);

  /**
   * specify value to be set. Default is null. If value isn't defined then command will be invoked in reading mode,
   * property won't be modified and current value will be returned by #call() method
   */
  @NotNull
  GitConfigCommand setValue(@Nullable String value);

  @NotNull
  GitConfigCommand setScope(@NotNull Scope scope);

  @NotNull String call() throws VcsException;

  @NotNull
  String callWithIgnoreExitCode() throws VcsException;

  enum Scope {
    LOCAL("--local"),
    GLOBAL("--global"),
    SYSTEM("--system");


    private final String myKey;

    Scope(String key) {
      myKey = key;
    }

    public String getKey() {
      return myKey;
    }
  }
}
