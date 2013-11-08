package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyVcsRootSshKeyManager implements VcsRootSshKeyManager {
  @Nullable
  public String getKey(@NotNull VcsRoot root) {
    return null;
  }
}
