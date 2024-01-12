

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyVcsRootSshKeyManager implements VcsRootSshKeyManager {
  @Nullable
  public TeamCitySshKey getKey(@NotNull VcsRoot root) {
    return null;
  }
}