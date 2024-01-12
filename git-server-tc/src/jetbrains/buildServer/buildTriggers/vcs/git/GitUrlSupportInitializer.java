

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.serverSide.ProjectManager;
import org.jetbrains.annotations.NotNull;

public class GitUrlSupportInitializer {
  public GitUrlSupportInitializer(@NotNull GitUrlSupport urlSupport, @NotNull ProjectManager projectManager, @NotNull ExtensionsProvider extensionsProvider) {
    urlSupport.setProjectManager(projectManager);
    urlSupport.setExtensionsProvider(extensionsProvider);
  }
}