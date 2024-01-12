

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsOperationProgress;
import org.jetbrains.annotations.NotNull;

public class GitVcsOperationProgress implements GitProgress {

  private final VcsOperationProgress myProgress;

  public GitVcsOperationProgress(@NotNull VcsOperationProgress progress) {
    myProgress = progress;
  }

  public void reportProgress(@NotNull String progress) {
    myProgress.reportProgress(progress);
  }

  public void reportProgress(final float progressPercents, @NotNull final String stage) {
    if (progressPercents < 0) {
      myProgress.reportProgress(stage);
    } else {
      int percents = (int) Math.floor(progressPercents * 100);
      myProgress.reportProgress(stage + " " + percents + "%");
    }
  }
}