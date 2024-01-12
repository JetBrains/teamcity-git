

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SshKeyManagerProviderImpl implements VcsRootSshKeyManagerProvider {

  private final ExtensionsProvider myExtensions;

  public SshKeyManagerProviderImpl(@NotNull ExtensionsProvider extensions) {
    myExtensions = extensions;
  }

  @Nullable
  public VcsRootSshKeyManager getSshKeyManager() {
    Collection<VcsRootSshKeyManager> managers = myExtensions.getExtensions(VcsRootSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    return managers.iterator().next();
  }
}