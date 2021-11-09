package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface GitRepoOperations {
  @NotNull
  FetchCommand fetchCommand(@NotNull String repoUrl);

  @NotNull
  LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl);

  @NotNull
  PushCommand pushCommand(@NotNull String repoUrl);

  @NotNull
  TagCommand tagCommand(@NotNull GitVcsSupport vcsSupport, @NotNull String repoUrl);

  @NotNull
  GitExec detectGit() throws VcsException;

  boolean isNativeGitOperationsEnabled(@NotNull String repoUrl);

  boolean isNativeGitOperationsEnabled();

  boolean isNativeGitOperationsSupported(@NotNull GitExec gitExec);
}
