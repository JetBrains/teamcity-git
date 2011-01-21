/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/**
 * @author dmitry.neverov
 */
public final class CommandLineUtil {

  private CommandLineUtil() {}


  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull ExecResult res) {
    if (res.getExitCode() != 0 || res.getException() != null) {
      Throwable exception = res.getException();
      String stderr = res.getStderr();
      String stdout = res.getStdout();
      final String message = "'" + cmdName + "' command failed.\n" +
              (!StringUtil.isEmpty(stderr) ? "stderr: " + stderr + "\n" : "") +
              (!StringUtil.isEmpty(stdout) ? "stdout: " + stdout + "\n" : "") +
              (exception != null ? "exception: " + exception.getLocalizedMessage() : "");
      return new VcsException(message);
    } else {
      return null;
    }
  }
  
}
