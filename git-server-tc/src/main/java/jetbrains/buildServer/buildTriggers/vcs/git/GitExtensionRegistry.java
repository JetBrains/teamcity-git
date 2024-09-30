

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collection;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.metrics.ServerMetrics;
import org.jetbrains.annotations.NotNull;

public class GitExtensionRegistry {

  public GitExtensionRegistry(@NotNull GitVcsSupport git,
                              @NotNull ServerMetrics serverMetrics,
                              @NotNull ExtensionHolder extensionHolder,
                              @NotNull Collection<GitServerExtension> extensions) {
    git.setExtensionHolder(extensionHolder);
    git.addExtensions(extensions);
    git.setServerMetrics(serverMetrics);
  }
}