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
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * The base class for git commands.
 *
 * @author pavel
 */
public class BaseCommand {
  private final CommandSettings myCommandSettings ;
  private String myWorkDirectory;

  public BaseCommand(@NotNull final CommandSettings settings) {
    myCommandSettings = settings;
    myWorkDirectory = settings.getLocalRepositoryDir().getAbsolutePath();
  }

  public CommandSettings getCommandSettings() {
    return myCommandSettings;
  }

  /**
   * Sets new working directory, by default working directory is taken from the AgentSettings#getLocalRepositoryDir
   *
   * @param workDirectory work dir
   */
  public void setWorkDirectory(final String workDirectory) {
    myWorkDirectory = workDirectory;
  }

  protected GeneralCommandLine createCommandLine() {
    GeneralCommandLine cli = new GeneralCommandLine();
    cli.setExePath(getCommandSettings().getGitCommandPath());
    cli.setWorkDirectory(myWorkDirectory);
    return cli;
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli) throws VcsException {
    return CommandUtil.runCommand(cli);
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli, int executionTimeout) throws VcsException {
    return CommandUtil.runCommand(cli, executionTimeout);
  }

  protected void failIfNotEmptyStdErr(@NotNull GeneralCommandLine cli, @NotNull ExecResult res) throws VcsException {
    if (!StringUtil.isEmpty(res.getStderr())) {
      CommandUtil.commandFailed(cli.getCommandLineString(), res);
    }
  }

}
