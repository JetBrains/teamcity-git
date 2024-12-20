package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitNativeOperationsStatus;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface GitRepoOperations extends GitNativeOperationsStatus {
  @NotNull
  FetchCommand fetchCommand(@NotNull String repoUrl);

  @NotNull
  LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl);

  @NotNull
  LsRemoteCommand lsRemoteCommand(boolean nativeOperations);

  @NotNull
  PushCommand pushCommand(@NotNull String repoUrl);

  InitCommandServer initCommand();

  AddCommandServer addCommand();

  LocalCommitCommandServer commitCommand();

  RepackCommandServer repackCommand();

  ConfigCommand configCommand();

  StatusCommandServer statusCommand(@NotNull String repoUrl);

  @NotNull
  ChangedPathsCommand diffCommand();

  @NotNull
  TagCommand tagCommand(@NotNull GitVcsSupport vcsSupport, @NotNull String repoUrl);

  @NotNull
  GitExec detectGit() throws VcsException;

  boolean isNativeGitOperationsEnabled(@NotNull String repoUrl);

  boolean isNativeGitOperationsSupported(@NotNull GitExec gitExec);
}
