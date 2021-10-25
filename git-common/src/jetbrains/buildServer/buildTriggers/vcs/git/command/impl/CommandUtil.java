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

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessTimeoutException;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.CheckoutCanceledException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jetbrains.buildServer.util.FileUtil.normalizeSeparator;

public class CommandUtil {
  public static final int DEFAULT_COMMAND_TIMEOUT_SEC = 3600;

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  private static void checkCommandFailed(@NotNull GitCommandLine cmd, @NotNull String cmdName, @NotNull ExecResult res) throws VcsException {
    if (cmd.isAbnormalExitExpected() && res.getExitCode() != 0 && res.getException() == null) {
      logMessage(cmdName + " exit code is " + res.getExitCode() + ": it is expected behaviour.", "info");
    } else if (res.getExitCode() != 0 || res.getException() != null) {
      commandFailed(cmdName, res);
    } else if (res.getStderr().length() > 0) {
      if (cmd.isStdErrExpected()) {
        logMessage("Error output produced by " + cmdName + ":\n" + res.getStderr().trim(), cmd.getStdErrLogLevel());
      } else {
        commandFailed(cmdName, res);
      }
    }
  }

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  private static void commandFailed(final String cmdName, final ExecResult res) throws VcsException {
    Throwable exception = res.getException();
    String stderr = res.getStderr().trim();
    String stdout = res.getStdout().trim();
    int exitCode = res.getExitCode();
    String message = cmdName + " command failed." +
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
      message += "\n" + stackWriter;
    }
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

  public static ExecResult runCommand(@NotNull GitCommandLine cli) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, byte[] input) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC, input);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, int timeoutSeconds) throws VcsException {
    return runCommand(cli, timeoutSeconds, new byte[0]);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, int timeoutSeconds, byte[] input) throws VcsException {
    int attemptsLeft = 2;
    while (true) {
      try {
        cli.checkCanceled();

        final String cmdStr = cli.getCommandLineString();
        final String fullCmdStr = getFullCmdStr(cli);
        Loggers.VCS.debug(fullCmdStr + (cli.getContext().isDebugGitCommands() ? " with env " + cli.getEnvParams() : ""));
        cli.logStart(cmdStr);

        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = cli.createStderrBuffer();
        ExecResult res = SimpleCommandLineProcessRunner
          .runCommandSecure(cli, cli.getCommandLineString(), input, new ProcessTimeoutCallback(timeoutSeconds, cli.getMaxOutputSize()), stdoutBuffer, stderrBuffer);

        cli.logFinish(cmdStr);
        CommandUtil.checkCommandFailed(cli, fullCmdStr, res);

        final String out = res.getStdout().trim();
        if (StringUtil.isNotEmpty(out)) {
          if (cli.getContext().isDebugGitCommands() || out.length() < 1024) {
            Loggers.VCS.debug("Output produced by " + fullCmdStr + ":\n" + out);
          }
        }
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

  @NotNull
  private static String getFullCmdStr(@NotNull GitCommandLine cli) {
    final File workingDir = cli.getWorkingDirectory();
    String path;
    if (workingDir == null) {
      path = "";
    } else {
      path = normalizeSeparator(workingDir.getAbsolutePath());
      for (String knownFolder : cli.getContext().getKnownRepoLocations()) {
        knownFolder = normalizeSeparator(knownFolder);
        if (path.contains(knownFolder)) {
          path = path.substring(path.indexOf(knownFolder) + knownFolder.length() + 1); // we need only folder name
          break;
        }
      }
    }
    return "[" + path + "] " + cli.getCommandLineString();
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
           msg.contains("server does not allow request for unadvertised object");
  }

  public static boolean shouldFetchFromScratch(@NotNull VcsException e) {
    if (e instanceof GitExecTimeout || CommandUtil.isCanceledError(e)) return false;
    return !isRemoteAccessError(e);
  }

  @NotNull
  public static List<String> splitByLines(@NotNull String str) {
    final int len = str.length();
    if (len == 0) return Collections.emptyList();

    final List<String> res = new ArrayList<>();
    int start = 0;
    while (start < len) {
      int lfIndex = str.indexOf('\n', start);
      if (lfIndex < 0) {
        lfIndex = len;
      }
      final String line = str.substring(start, lfIndex);
      if (line.length() > 0) {
        res.add(line);
      }
      start = lfIndex + 1;
    }
    return res;
  }
}
