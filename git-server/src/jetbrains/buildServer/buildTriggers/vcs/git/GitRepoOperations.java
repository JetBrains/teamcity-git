package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public interface GitRepoOperations {
  @NotNull
  FetchCommand fetchCommand();

  @NotNull
  LsRemoteCommand lsRemoteCommand();
}
