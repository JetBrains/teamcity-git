

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.FetchService;
import org.jetbrains.annotations.NotNull;

public class FetchCallbackProgress implements GitProgress {
  private final FetchService.FetchRepositoryCallback myCallback;

  public FetchCallbackProgress(@NotNull FetchService.FetchRepositoryCallback callback) {
    myCallback = callback;
  }

  public void reportProgress(@NotNull final String progress) {
  }

  public void reportProgress(final float progressPercents, @NotNull final String stage) {
    myCallback.update(progressPercents, stage);
  }
}