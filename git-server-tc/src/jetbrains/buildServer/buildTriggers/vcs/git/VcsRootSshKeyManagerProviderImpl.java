package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.SshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstancesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class VcsRootSshKeyManagerProviderImpl implements VcsRootSshKeyManagerProvider {

  private final ExtensionsProvider myExtensions;
  private final VcsRootInstancesManager myVcsRootInstanceManager;
  private ServerSshKeyManager mySshKeyManager;

  public VcsRootSshKeyManagerProviderImpl(@NotNull ExtensionsProvider extensions,
                                          @NotNull VcsRootInstancesManager vcsRootInstanceManager) {
    myExtensions = extensions;
    myVcsRootInstanceManager = vcsRootInstanceManager;
  }

  @Nullable
  public SshKeyManager getSshKeyManager(@NotNull VcsRoot root) {
    ServerSshKeyManager sshKeyManager = getSshKeyManager();
    if (sshKeyManager == null)
      return null;
    VcsRootInstance rootInstance = getVcsRootInstance(root);
    if (rootInstance == null)
      return null;
    SVcsRoot parent = rootInstance.getParent();
    if (parent.getId() < 0)
      return null;
    SProject rootProject = parent.getProject();
    return new VcsRootSshKeyManager(sshKeyManager, rootProject);
  }


  private VcsRootInstance getVcsRootInstance(@NotNull VcsRoot root) {
    if (root instanceof VcsRootInstance)
      return (VcsRootInstance) root;
    return myVcsRootInstanceManager.findRootInstanceById(root.getId());
  }


  @Nullable
  private ServerSshKeyManager getSshKeyManager() {
    if (mySshKeyManager != null)
      return mySshKeyManager;
    Collection<ServerSshKeyManager> managers = myExtensions.getExtensions(ServerSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    mySshKeyManager = managers.iterator().next();
    return mySshKeyManager;
  }


  private final static class VcsRootSshKeyManager implements SshKeyManager {
    private final ServerSshKeyManager mySshKeyManager;
    private final SProject myProject;

    private VcsRootSshKeyManager(@NotNull ServerSshKeyManager sshKeyManager, @NotNull SProject project) {
      mySshKeyManager = sshKeyManager;
      myProject = project;
    }

    @Nullable
    public TeamCitySshKey getKey(@NotNull String keyId) {
      return mySshKeyManager.getKey(myProject, keyId);
    }
  }
}
