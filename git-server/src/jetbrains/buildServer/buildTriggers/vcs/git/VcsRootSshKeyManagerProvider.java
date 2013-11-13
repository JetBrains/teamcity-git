package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VcsRootSshKeyManagerProvider extends GitServerExtension {

  @Nullable
  VcsRootSshKeyManager getSshKeyManager(@NotNull VcsRoot root);
}
