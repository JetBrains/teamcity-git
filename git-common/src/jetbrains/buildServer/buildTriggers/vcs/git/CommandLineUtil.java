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

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public final class CommandLineUtil {

  private CommandLineUtil() {}


  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull ExecResult res) {
    return getCommandLineError(cmdName, res, true, true);
  }


  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull ExecResult res, boolean includeStdOut, boolean includeStdErr) {
    return getCommandLineError(cmdName, "", res, includeStdOut, includeStdErr);
  }

  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull String details, @NotNull ExecResult res, boolean includeStdOut, boolean includeStdErr) {
    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable exception = res.getException();
    int exitCode = res.getExitCode();
    if (exitCode != 0 || exception != null) {
      String stderr = res.getStderr();
      String stdout = res.getStdout();
      final String message = "'" + cmdName + "' command failed" + details + "." +
                             (exception != null ? "\nexception: " + exception.getMessage() : "") +
                             (includeStdErr && !StringUtil.isEmpty(stderr) ? "\nstderr: " + stderr.trim() : "") +
                             (includeStdOut && !StringUtil.isEmpty(stdout) ? "\nstdout: " + stdout.trim() : "") +
                             (exitCode != 0 ? "\nexit code: " + exitCode : "");
      return new VcsException(message);
    } else {
      return null;
    }
  }
}
