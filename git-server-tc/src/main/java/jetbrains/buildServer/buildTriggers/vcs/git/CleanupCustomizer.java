

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.IOGuard;
import org.jetbrains.annotations.NotNull;

public class CleanupCustomizer {

  public CleanupCustomizer(@NotNull Cleanup cleanup) {
    cleanup.setCleanupCallWrapper(cleanupRunner -> IOGuard.allowCommandLine(cleanupRunner::run));
  }
}