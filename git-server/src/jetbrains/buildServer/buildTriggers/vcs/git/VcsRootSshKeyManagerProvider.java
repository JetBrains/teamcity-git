package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ssh.SshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VcsRootSshKeyManagerProvider extends GitServerExtension {

  @Nullable
  SshKeyManager getSshKeyManager(@NotNull VcsRoot root);
}
