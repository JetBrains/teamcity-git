package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VcsRootSshKeyManager {

  @Nullable
  String getKey(@NotNull VcsRoot root);

}
