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
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class CommandUtil {
  public static final int DEFAULT_COMMAND_TIMEOUT_SEC = 3600;

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  public static void checkCommandFailed(@NotNull String cmdName, @NotNull ExecResult res, String... errorsLogLevel) throws VcsException {
    if (res.getExitCode() != 0 || res.getException() != null) {
      commandFailed(cmdName, res);
    }
    if (res.getStderr().length() > 0) {
      logMessage("Error output produced by: " + cmdName, errorsLogLevel);
      logMessage(res.getStderr().trim(), errorsLogLevel);
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
    String theLevel;
    if (level.length > 0) {
      theLevel = level[0];
    } else {
      theLevel = "warn";
    }
    if (theLevel.equals("warn")) {
      Loggers.VCS.warn(message);
    } else if (theLevel.equals("debug")) {
      Loggers.VCS.debug(message);
    }
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, final String... errorsLogLevel) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC, errorsLogLevel);
  }

  public static ExecResult runCommand(@NotNull GitCommandLine cli, int timeoutSeconds, final String... errorsLogLevel) throws VcsException {
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
        ExecResult res = SimpleCommandLineProcessRunner.runCommandSecure(cli, cli.getCommandLineString(), null, new ProcessTimeoutCallback(timeoutSeconds), stdoutBuffer, stderrBuffer);
        cli.logFinish(cmdStr);
        CommandUtil.checkCommandFailed(cmdStr, res, errorsLogLevel);
        String out = res.getStdout().trim();
        Loggers.VCS.debug(out);
        if (!isEmpty(out) || !cli.isRepeatOnEmptyOutput() || attemptsLeft <= 0)
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
    String msg = e.getMessage();
    return msg != null && msg.contains("exception: jetbrains.buildServer.ProcessTimeoutException");
  }

  public static boolean isCanceledError(@NotNull VcsException e) {
    return e instanceof CheckoutCanceledException || e.getCause() instanceof InterruptedException;
  }
}
