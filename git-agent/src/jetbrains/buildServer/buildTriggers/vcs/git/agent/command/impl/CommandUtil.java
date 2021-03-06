/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.*;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessTimeoutException;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.ShallowUpdater;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class CommandUtil {
  public static final int DEFAULT_COMMAND_TIMEOUT_SEC = 3600;

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  public static void checkCommandFailed(@NotNull String cmdName, @NotNull ExecResult res, String... errorsLogLevel) throws VcsException {
    if ("info".equals(logLevel(errorsLogLevel)) && res.getExitCode() != 0 && res.getException() == null) {
      logMessage("'" + cmdName + "' exit code: " + res.getExitCode() + ". It is expected behaviour.", "info");
    } else {
      if (res.getExitCode() != 0 || res.getException() != null) {
        commandFailed(cmdName, res);
      }
      if (res.getStderr().length() > 0) {
        logMessage("Error output produced by: " + cmdName, errorsLogLevel);
        logMessage(res.getStderr().trim(), errorsLogLevel);
      }
    }
  }

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  public static void commandFailed(final String cmdName, final ExecResult res, String... errorsLogLevel) throws VcsException {
    Throwable exception = res.getException();
    String stderr = res.getStderr().trim();
    String stdout = res.getStdout().trim();
    int exitCode = res.getExitCode();
    String message = "'" + cmdName + "' command failed." +
                     (exitCode != 0 ? "\nexit code: " + exitCode : "") +
                     (!StringUtil.isEmpty(stdout) ? "\n" + "stdout: " + stdout : "") +
                     (!StringUtil.isEmpty(stderr) ? "\n" + "stderr: " + stderr : "");
    if (exception != null) {
      message += "\nexception: ";
      message += exception.getClass().getName();
      String exceptionMessage = exception.getMessage();
      if (!StringUtil.isEmpty(exceptionMessage))
        message += ": " + exceptionMessage;
    }
    if (exception != null && isImportant(exception)) {
      Writer stackWriter = new StringWriter();
      exception.printStackTrace(new PrintWriter(stackWriter));
      message += "\n" + stackWriter.toString();
    }
    logMessage(message, errorsLogLevel);
    if (exception != null)
      throw new VcsException(message, exception);
    throw new VcsException(message);
  }

  private static boolean isImportant(Throwable e) {
    return e instanceof NullPointerException;
  }

  /**
   * Log message using level, if level is not set - use WARN
   *
   * @param message message to log
   * @param level   level to use
   */
  private static void logMessage(String message, String... level) {
    final String theLevel = logLevel(level);
    if (theLevel.equals("warn")) {
      Loggers.VCS.warn(message);
    } else if (theLevel.equals("debug")) {
      Loggers.VCS.debug(message);
    } else if (theLevel.equals("info")) {
      Loggers.VCS.info(message);
    }
  }

  private static String logLevel(String... level) {
    return level.length > 0 ? level[0] : "warn";
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, final String... errorsLogLevel) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC, errorsLogLevel);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, byte[] input, final String... errorsLogLevel) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC, input, errorsLogLevel);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, int timeoutSeconds, final String... errorsLogLevel)
    throws VcsException {
    return runCommand(cli, timeoutSeconds, new byte[0], errorsLogLevel);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, int timeoutSeconds, byte[] input, final String... errorsLogLevel)
    throws VcsException {
    int attemptsLeft = 2;
    while (true) {
      try {
        cli.checkCanceled();
        String cmdStr = cli.getCommandLineString();
        File workingDir = cli.getWorkingDirectory();
        String inDir = workingDir != null ? "[" + workingDir.getAbsolutePath() + "]" : "";
        String msg = inDir + ": " + cmdStr;
        Loggers.VCS.info(msg);
        cli.logStart(cmdStr);
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = cli.createStderrBuffer();
        ExecResult res = SimpleCommandLineProcessRunner
          .runCommandSecure(cli, cli.getCommandLineString(), input, new ProcessTimeoutCallback(timeoutSeconds, cli.getMaxOutputSize()), stdoutBuffer, stderrBuffer);
        cli.logFinish(cmdStr);
        CommandUtil.checkCommandFailed(cmdStr, res, errorsLogLevel);
        String out = res.getStdout();
        Loggers.VCS.debug(out);
        if (!StringUtil.isEmptyOrSpaces(out) || !cli.isRepeatOnEmptyOutput() || attemptsLeft <= 0)
          return res;
        Loggers.VCS.warn("Get an unexpected empty output, will repeat command, attempts left: " + attemptsLeft);
        attemptsLeft--;
      } finally {
        for (Runnable action : cli.getPostActions()) {
          action.run();
        }
      }
    }
  }

  public static void failIfNotEmptyStdErr(@NotNull GeneralCommandLine cli, @NotNull ExecResult res, String... errorsLogLevel) throws VcsException {
    if (!isEmpty(res.getStderr()))
      CommandUtil.commandFailed(cli.getCommandLineString(), res, errorsLogLevel);
  }


  public static boolean isTimeoutError(@NotNull VcsException e) {
    return isMessageContains(e, "exception: jetbrains.buildServer.ProcessTimeoutException");
  }

  public static boolean isCanceledError(@NotNull VcsException e) {
    return e instanceof CheckoutCanceledException || e.getCause() instanceof InterruptedException;
  }

  public static boolean isNoSuchFileOrDirError(@NotNull VcsException e) {
    return isMessageContains(e, "No such file or directory");
  }

  public static boolean isFileNameTooLongError(@NotNull VcsException e) {
    return isMessageContains(e, "Filename too long");
  }

  public static boolean isMessageContains(@NotNull VcsException e, @NotNull String text) {
    final String msg = e.getMessage();
    return msg != null && StringUtil.containsIgnoreCase(msg, text);
  }

  private static boolean isConnectionRefused(@NotNull VcsException e) {
    return isMessageContains(e, "Connection refused");
  }

  private static boolean isConnectionReset(@NotNull VcsException e) {
    return isMessageContains(e, "Connection reset");
  }

  public static boolean isRecoverable(@NotNull Exception e) {
    if (e instanceof ProcessTimeoutException || e instanceof GitExecTimeout) return true;
    if (!(e instanceof VcsException)) return false;

    final VcsException ve = (VcsException)e;
    if (isTimeoutError(ve) || isConnectionRefused(ve) || isConnectionReset(ve)) return true;
    if (isCanceledError(ve)) return false;
    if (e instanceof GitIndexCorruptedException) return false;

    return !isRemoteAccessError(ve);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isRemoteAccessError(@NotNull VcsException e) {
    final String msg = e.getMessage().toLowerCase();
    return msg.contains("couldn't find remote ref") ||
           msg.contains("no remote repository specified") ||
           msg.contains("no such remote") ||
           msg.contains("access denied") ||
           msg.contains("could not read from remote repository") ||
           msg.contains(ShallowUpdater.REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED);
  }

  public static boolean shouldFetchFromScratch(@NotNull VcsException e) {
    if (e instanceof GitExecTimeout || CommandUtil.isCanceledError(e)) return false;
    return !isRemoteAccessError(e);
  }
}
