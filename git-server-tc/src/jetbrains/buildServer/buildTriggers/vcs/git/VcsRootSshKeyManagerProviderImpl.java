package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class VcsRootSshKeyManagerProviderImpl implements VcsRootSshKeyManagerProvider {

  private final ExtensionsProvider myExtensions;
  private VcsRootSshKeyManager mySshKeyManager;

  public VcsRootSshKeyManagerProviderImpl(@NotNull ExtensionsProvider extensions) {
    myExtensions = extensions;
  }

  @Nullable
  public VcsRootSshKeyManager getSshKeyManager(@NotNull VcsRoot root) {
    if (mySshKeyManager != null)
      return mySshKeyManager;
    Collection<VcsRootSshKeyManager> managers = myExtensions.getExtensions(VcsRootSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    mySshKeyManager = managers.iterator().next();
    return mySshKeyManager;
  }
}
