package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GitRepoOperations {
  @NotNull
  FetchCommand fetchCommand(@NotNull String repoUrl);

  @NotNull
  LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl);

  @NotNull
  PushCommand pushCommand(@NotNull String repoUrl);

  @Nullable
  GitExec gitExec();

  boolean isNativeGitOperationsEnabled();

  boolean isNativeGitOperationsSupported();
}
