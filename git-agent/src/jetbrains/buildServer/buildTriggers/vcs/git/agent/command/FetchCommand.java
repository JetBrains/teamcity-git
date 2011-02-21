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
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The "git fetch" command
 */
public class FetchCommand extends RepositoryCommand {
  /**
   * The fetch timeout (24 hours)
   */
  static final int TIMEOUT = 24 * 60 * 60;
  /**
   * Agent's SSH service
   */
  private final GitAgentSSHService mySsh;
  /**
   * Do fetch into branches with same spec as in remote repository. Used for bare repositories which we use as a mirrors of remotes.
   */
  private final boolean myMirror;

  /**
   * The constructor
   *
   * @param settings the settings object
   * @param ssh      the SSH service
   */
  public FetchCommand(@NotNull final AgentSettings settings, @NotNull final GitAgentSSHService ssh) {
    super(settings);
    mySsh = ssh;
    myMirror = false;
  }

  public FetchCommand(@NotNull final AgentSettings settings, @NotNull final GitAgentSSHService ssh, String bareRepositoryDir) {
    super(settings, bareRepositoryDir);
    mySsh = ssh;
    myMirror = true;
  }


  /**
   * Perform fetch operation according to settings
   *
   * @throws VcsException the VCS exception
   */
  public void fetch() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("fetch");
    cmd.addParameters("--no-tags", "-q", "origin");
    cmd.addParameter(getRefSpec());
    if (mySettings.isUseNativeSSH()) {
      runCommand(cmd, TIMEOUT);
    } else {
      SshHandler h = new SshHandler(mySsh, cmd);
      try {
        runCommand(cmd, TIMEOUT);
      } finally {
        h.unregister();
      }
    }
  }

  private String getRefSpec() {
    AgentSettings s = getSettings();
    if (myMirror) {
      return "+" + GitUtils.branchRef(s.getBranch()) + ":" + GitUtils.branchRef(s.getBranch());
    } else {
      return "+" + GitUtils.branchRef(s.getBranch()) + ":" + GitUtils.remotesBranchRef(s.getBranch());
    }
  }

}
