package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExtensionHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GitExtensionRegistry {

  public GitExtensionRegistry(@NotNull GitVcsSupport git,
                              @NotNull ExtensionHolder extensionHolder,
                              @NotNull Collection<GitServerExtension> extensions) {
    git.setExtensionHolder(extensionHolder);
    git.setExtensions(extensions);
  }
}
