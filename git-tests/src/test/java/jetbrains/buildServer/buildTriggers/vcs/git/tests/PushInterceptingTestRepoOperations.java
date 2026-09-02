package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Delegates every git operation to a real implementation, but lets a test interfere with the push: run something
 * right before it, as another writer would, and fail it afterwards, as a lost answer would. Everything a push
 * races with happens between reading the destination branch and updating it, and that window is not reachable
 * from the outside otherwise.
 */
public class PushInterceptingTestRepoOperations implements GitRepoOperations {

  public interface Action {
    void run() throws Exception;
  }

  @NotNull private final GitRepoOperations myDelegate;
  @Nullable private Action myBeforePush;
  @Nullable private String myFailureAfterPush;

  public PushInterceptingTestRepoOperations(@NotNull GitRepoOperations delegate) {
    myDelegate = delegate;
  }

  /**
   * Runs the action right before the push reaches the remote repository.
   */
  @NotNull
  public PushInterceptingTestRepoOperations beforePush(@NotNull Action action) {
    myBeforePush = action;
    return this;
  }

  /**
   * Reports the push as failed although it has been performed, the way a lost answer looks to the caller.
   */
  @NotNull
  public PushInterceptingTestRepoOperations failAfterPush(@NotNull String message) {
    myFailureAfterPush = message;
    return this;
  }

  @NotNull
  @Override
  public PushCommand pushCommand(@NotNull String repoUrl) {
    final PushCommand delegate = myDelegate.pushCommand(repoUrl);
    return (db, gitRoot, ref, commit, lastCommit) -> {
      if (myBeforePush != null) {
        try {
          myBeforePush.run();
        } catch (Exception e) {
          throw new VcsException("Failed to run the action before the push", e);
        }
      }
      CommitResult result = delegate.push(db, gitRoot, ref, commit, lastCommit);
      if (myFailureAfterPush != null)
        throw new VcsException(myFailureAfterPush);
      return result;
    };
  }

  @NotNull
  @Override
  public FetchCommand fetchCommand(@NotNull String repoUrl) {
    return myDelegate.fetchCommand(repoUrl);
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl) {
    return myDelegate.lsRemoteCommand(repoUrl);
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand(boolean nativeOperations) {
    return myDelegate.lsRemoteCommand(nativeOperations);
  }

  @Override
  public InitCommandServer initCommand() {
    return myDelegate.initCommand();
  }

  @Override
  public AddCommandServer addCommand() {
    return myDelegate.addCommand();
  }

  @Override
  public LocalCommitCommandServer commitCommand() {
    return myDelegate.commitCommand();
  }

  @Override
  public RepackCommandServer repackCommand() {
    return myDelegate.repackCommand();
  }

  @Override
  public ConfigCommand configCommand() {
    return myDelegate.configCommand();
  }

  @Override
  public StatusCommandServer statusCommand(@NotNull String repoUrl) {
    return myDelegate.statusCommand(repoUrl);
  }

  @Override
  public FsckCommandServer fsckCommand() {
    return myDelegate.fsckCommand();
  }

  @NotNull
  @Override
  public ChangedPathsCommand changedPathsCommand() {
    return myDelegate.changedPathsCommand();
  }

  @NotNull
  @Override
  public TagCommand tagCommand(@NotNull GitVcsSupport vcsSupport, @NotNull String repoUrl) {
    return myDelegate.tagCommand(vcsSupport, repoUrl);
  }

  @NotNull
  @Override
  public GitExec detectGit() throws VcsException {
    return myDelegate.detectGit();
  }

  @Override
  public boolean isNativeGitOperationsEnabled(@NotNull String repoUrl) {
    return myDelegate.isNativeGitOperationsEnabled(repoUrl);
  }

  @Override
  public boolean isNativeGitOperationsSupported(@NotNull GitExec gitExec) {
    return myDelegate.isNativeGitOperationsSupported(gitExec);
  }

  @Override
  public boolean isNativeGitOperationsEnabled() {
    return myDelegate.isNativeGitOperationsEnabled();
  }

  @Override
  public boolean setNativeGitOperationsEnabled(boolean nativeGitOperationsEnabled) {
    return myDelegate.setNativeGitOperationsEnabled(nativeGitOperationsEnabled);
  }
}
