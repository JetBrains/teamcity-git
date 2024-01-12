

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public interface GitProgressLogger {

  void openBlock(@NotNull String name);

  void message(@NotNull String message);

  void warning(@NotNull String message);

  void progressMessage(@NotNull String message);

  void closeBlock(@NotNull String name);

  GitProgressLogger NO_OP = new GitProgressLogger() {
    public void openBlock(@NotNull final String name) {
    }
    public void message(@NotNull final String message) {
    }
    public void warning(@NotNull final String message) {
    }
    public void progressMessage(@NotNull final String message) {
    }
    public void closeBlock(@NotNull final String name) {
    }
  };
}