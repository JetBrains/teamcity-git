

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public interface GitProgress {

  void reportProgress(@NotNull String progress);

  void reportProgress(float progressPercents, @NotNull String stage);

  static GitProgress NO_OP = new GitProgress() {
    public void reportProgress(@NotNull final String progress) {
    }
    public void reportProgress(final float progressPercents, @NotNull final String stage) {
    }
  };
}