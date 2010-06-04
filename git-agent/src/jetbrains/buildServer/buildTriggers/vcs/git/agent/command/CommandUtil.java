/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CommandUtil {
  private static final int DEFAULT_COMMAND_TIMEOUT_SEC = 3600;

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  public static void checkCommandFailed(@NotNull String cmdName, @NotNull ExecResult res) throws VcsException {
    if (res.getExitCode() != 0 || res.getException() != null) {
      commandFailed(cmdName, res);
    }
    if (res.getStderr().length() > 0) {
      Loggers.VCS.warn("Error output produced by: " + cmdName);
      Loggers.VCS.warn(res.getStderr().trim());
    }
  }

  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  public static void commandFailed(final String cmdName, final ExecResult res) throws VcsException {
    Throwable exception = res.getException();
    String stderr = res.getStderr().trim();
    String stdout = res.getStdout().trim();
    final String message = "'" + cmdName + "' command failed." +
            (!StringUtil.isEmpty(stderr) ? "\n" + "stderr: " + stderr : "") +
            (!StringUtil.isEmpty(stdout) ? "\n" + "stdout: " + stdout : "") +
            (exception != null ?  "\n" + "exception: " + exception.getLocalizedMessage() : "");
    Loggers.VCS.warn(message);
    throw new VcsException(message);
  }

  public static ExecResult runCommand(@NotNull GeneralCommandLine cli) throws VcsException {
    return runCommand(cli, DEFAULT_COMMAND_TIMEOUT_SEC);
  }

  public static ExecResult runCommand(@NotNull GeneralCommandLine cli, final int executionTimeout) throws VcsException {
    String cmdStr = cli.getCommandLineString();
    Loggers.VCS.debug("Run command: " + cmdStr);
    ExecResult res = SimpleCommandLineProcessRunner.runCommand(cli, null, new SimpleCommandLineProcessRunner.RunCommandEventsAdapter() {
      @Override
      public Integer getOutputIdleSecondsTimeout() {
        return executionTimeout;
      }
    });
    CommandUtil.checkCommandFailed(cmdStr, res);
    Loggers.VCS.debug(res.getStdout().trim());
    return res;
  }
}
