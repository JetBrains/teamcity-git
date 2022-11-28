package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.*;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.Retry;
import jetbrains.buildServer.buildTriggers.vcs.git.command.AuthCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.Errors;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitOutdatedIndexException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings.with;

public abstract class BaseAuthCommandImpl<T extends BaseCommand> extends BaseCommandImpl implements AuthCommand<T> {
  private boolean myUseNativeSsh;
  private int myTimeout = CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
  private AuthSettings myAuthSettings;
  private URIish myRepoUrl;
  private final List<Runnable> myPreActions = new ArrayList<Runnable>();
  private int myRetryAttempts = 1;
  private Map<String, String> myTraceEnv = Collections.emptyMap();

  public BaseAuthCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public T setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return (T)this;
  }

  @NotNull
  public T setTimeout(int timeout) {
    myTimeout = timeout;
    return (T)this;
  }

  @NotNull
  @Override
  public T setAuthSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return (T)this;
  }

  public T addPreAction(@NotNull Runnable action) {
    myPreActions.add(action);
    return (T)this;
  }

  @Override
  public T setRetryAttempts(int num) {
    myRetryAttempts = num;
    return (T)this;
  }


  @Override
  public T trace(@NotNull Map<String, String> gitTraceEnv) {
    myTraceEnv = gitTraceEnv;
    return (T)this;
  }

  @Override
  public T setRepoUrl(@NotNull URIish repoUrl) {
    myRepoUrl = repoUrl;
    return (T)this;
  }

  @NotNull
  protected ExecResult runCmd(@NotNull GitCommandLine cmd) throws VcsException {
    return runCmd(cmd, new byte[0]);
  }

  class GitCommandRetryable implements Retry.Retryable<ExecResult> {
    @NotNull protected byte[] myInput;
    @NotNull final protected GitCommandLine myCmd;
    GitCommandRetryable(@NotNull GitCommandLine cmd, @NotNull byte[] input) {
      myInput = Arrays.copyOf(input, input.length);
      myCmd = cmd;
    }

    @Override
    public boolean requiresRetry(@NotNull Exception e, int attempt, int maxAttempts) {
      return CommandUtil.isRecoverable(e, myAuthSettings, attempt, maxAttempts);
    }

    @Override
    public ExecResult call() throws Exception {
      for (Runnable action : myPreActions) {
        action.run();
      }
      return doRunCmd(myCmd, myInput);
    }

    @NotNull
    @Override
    public Logger getLogger() {
      return Loggers.VCS;
    }

  }

  @NotNull
  protected ExecResult runCmd(@NotNull GitCommandLine cmd, @NotNull byte[] input) throws VcsException {
    return runCmd(new GitCommandRetryable(cmd, input));
  }

  @NotNull
  protected ExecResult runCmd(@NotNull Retry.Retryable<ExecResult> retryable) throws VcsException {
    if (myAuthSettings == null) throw new VcsException("Authentication settings must be specified before calling this command");
    try {
      return Retry.retry(retryable, myRetryAttempts);
    } catch (Exception e) {
      if (e instanceof VcsException) throw (VcsException)e;
      throw new VcsException(e);
    }
  }

  @NotNull
  protected ExecResult doRunCmd(@NotNull GitCommandLine cmd, @NotNull byte[] input) throws VcsException {
    try {
      return cmd.run(with()
                       .timeout(myTimeout)
                       .authSettings(myAuthSettings)
                       .addInput(input)
                       .useNativeSsh(myUseNativeSsh)
                       .trace(myTraceEnv));
    } catch (VcsException e) {
      if (CommandUtil.isTimeoutError(e)) {
        throw new GitExecTimeout();
      }
      if (Errors.isCorruptedIndexError(e)) {
        File workingDir = cmd.getWorkingDirectory();
        File gitIndex = new File(new File(workingDir, ".git"), "index");
        throw new GitIndexCorruptedException(gitIndex, e);
      }
      if (Errors.isOutdatedIndexError(e)) {
        throw new GitOutdatedIndexException(e);
      }
      throw e;
    }
  }
}
