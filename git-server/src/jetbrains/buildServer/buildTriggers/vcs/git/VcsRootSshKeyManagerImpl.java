package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ssh.SshKeyManager;
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
    String id = root.getProperty(Constants.TEAMCITY_SSH_KEY_ID);
    if (id == null)
      return null;

    if (mySshManagerProvider == null)
      return null;

    SshKeyManager sshKeyManager = mySshManagerProvider.getSshKeyManager(root);
    if (sshKeyManager == null)
      return null;

    return sshKeyManager.getKey(id);
  }
}
