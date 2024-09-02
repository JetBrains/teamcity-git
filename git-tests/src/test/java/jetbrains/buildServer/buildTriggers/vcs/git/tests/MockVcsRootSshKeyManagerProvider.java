

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.VcsRootSshKeyManagerProvider;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.Nullable;

public class MockVcsRootSshKeyManagerProvider implements VcsRootSshKeyManagerProvider {
  @Nullable
  public VcsRootSshKeyManager getSshKeyManager() {
    return null;
  }
}