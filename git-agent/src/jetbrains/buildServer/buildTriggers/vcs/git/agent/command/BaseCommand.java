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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The base class for git commands.
 *
 * @author pavel
 */
public class BaseCommand {
  /**
   * The path to git
   */
  private final String myGitPath;
  /**
   * The work directory
   */
  private final String myWorkDirectory;

  public BaseCommand(String gitPath, String workDirectory) {
    myGitPath = gitPath;
    myWorkDirectory = workDirectory;
  }

  protected GeneralCommandLine createCommandLine() {
    GeneralCommandLine cli = new GeneralCommandLine();
    cli.setExePath(myGitPath);
    cli.setWorkDirectory(myWorkDirectory);
    return cli;
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli, String... errorsLogLevel) throws VcsException {
    return CommandUtil.runCommand(cli, errorsLogLevel);
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli, int executionTimeout, String... errorsLogLevel) throws VcsException {
    return CommandUtil.runCommand(cli, executionTimeout, errorsLogLevel);
  }

  protected void failIfNotEmptyStdErr(@NotNull GeneralCommandLine cli, @NotNull ExecResult res, String... errorsLogLevel) throws VcsException {
    if (!StringUtil.isEmpty(res.getStderr())) {
      CommandUtil.commandFailed(cli.getCommandLineString(), res, errorsLogLevel);
    }
  }

}
