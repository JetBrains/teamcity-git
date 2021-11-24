package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings.with;

public abstract class BaseAuthCommandImpl<T extends BaseCommand> extends BaseCommandImpl implements AuthCommand<T> {
  private boolean myUseNativeSsh;
  private int myTimeout = CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
  private AuthSettings myAuthSettings;
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

  @NotNull
  protected ExecResult runCmd(@NotNull GitCommandLine cmd) throws VcsException {
    return runCmd(cmd, new byte[0]);
  }

  @NotNull
  protected ExecResult runCmd(@NotNull GitCommandLine cmd, @NotNull byte[] input) throws VcsException {
    try {
      return Retry.retry(new Retry.Retryable<ExecResult>() {
        @Override
        public boolean requiresRetry(@NotNull Exception e, int attempt) {
          return CommandUtil.isRecoverable(e, myAuthSettings, attempt);
        }

        @Override
        public ExecResult call() throws Exception {
          for (Runnable action : myPreActions) {
            action.run();
          }
          return doRunCmd(cmd, input);
        }

        @NotNull
        @Override
        public Logger getLogger() {
          return Loggers.VCS;
        }
      }, myRetryAttempts);
    } catch (Exception e) {
      if (e instanceof VcsException) throw (VcsException)e;
      throw new VcsException(e);
    }
  }

  @NotNull
  private ExecResult doRunCmd(@NotNull GitCommandLine cmd, @NotNull byte[] input) throws VcsException {
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
