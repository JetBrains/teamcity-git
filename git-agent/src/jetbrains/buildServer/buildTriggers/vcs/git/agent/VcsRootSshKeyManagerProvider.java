

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.Nullable;

public interface VcsRootSshKeyManagerProvider {

  @Nullable
  VcsRootSshKeyManager getSshKeyManager();

}