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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The "git submodule" command
 *
 * @author dmitry.neverov
 */
public class SubmoduleCommand extends RepositoryCommand {

  /**
   * Agent's SSH service
   */
  private final GitAgentSSHService mySsh;

  public SubmoduleCommand(@NotNull final AgentSettings mySettings, @NotNull final GitAgentSSHService ssh) {
    super(mySettings);
    mySsh = ssh;
  }

  public SubmoduleCommand(@NotNull final AgentSettings mySettings, @NotNull final GitAgentSSHService ssh, String workingDirectory) {
    super(mySettings, workingDirectory);
    mySsh = ssh;
  }

  /**
   * Initialize git submodules
   *
   * @throws VcsException if there is a problem with running git
   */
  public void init() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("submodule");
    cmd.addParameter("init");
    runCommand(cmd);
  }


  /**
   * Checkout tracked commit from submodule repository
   *
   * @throws VcsException if there is a problem with running git
   */
  public void update() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("submodule");
    cmd.addParameter("update");
    if (mySettings.isUseNativeSSH()) {
      runCommand(cmd, FetchCommand.TIMEOUT);
    } else {
      SshHandler h = new SshHandler(mySsh, cmd);
      try {
        runCommand(cmd, FetchCommand.TIMEOUT);
      } finally {
        h.unregister();
      }
    }
  }

}
