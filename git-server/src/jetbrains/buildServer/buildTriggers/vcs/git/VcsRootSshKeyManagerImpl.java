package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

public class VcsRootSshKeyManagerImpl implements VcsRootSshKeyManager {

  private VcsRootSshKeyManagerProvider mySshManagerProvider;

  @Autowired(required=false)
  public void setSshManagerProvider(@NotNull VcsRootSshKeyManagerProvider sshManagerProvider) {
    mySshManagerProvider = sshManagerProvider;
  }

  @Nullable
  public TeamCitySshKey getKey(@NotNull VcsRoot root) {
    if (mySshManagerProvider == null)
      return null;

    VcsRootSshKeyManager sshKeyManager = mySshManagerProvider.getSshKeyManager(root);
    if (sshKeyManager == null)
      return null;

    return sshKeyManager.getKey(root);
  }
}
