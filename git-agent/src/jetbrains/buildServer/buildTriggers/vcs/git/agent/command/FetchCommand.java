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

  private final int myTimeout;
  private final GitAgentSSHService mySsh;
  private final boolean myMirror;

  public FetchCommand(@NotNull final AgentSettings settings, @NotNull final GitAgentSSHService ssh, final int timeout) {
    super(settings);
    mySsh = ssh;
    myMirror = false;
    myTimeout = timeout;
  }

  public FetchCommand(@NotNull final AgentSettings settings, @NotNull final GitAgentSSHService ssh, String bareRepositoryDir, final int timeout) {
    super(settings, bareRepositoryDir);
    mySsh = ssh;
    myMirror = true;
    myTimeout = timeout;
  }


  public void fetch(boolean silent) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("fetch");
    addOptions(cmd, silent);
    cmd.addParameter("origin");
    cmd.addParameter(getRefSpec());
    if (mySettings.isUseNativeSSH()) {
      runCommand(cmd, myTimeout);
    } else {
      SshHandler h = new SshHandler(mySsh, cmd);
      try {
        runCommand(cmd, myTimeout);
      } finally {
        h.unregister();
      }
    }
  }


  private void addOptions(GeneralCommandLine cmd, boolean silent) {
    if (silent)
      cmd.addParameter("-q");
    else
      cmd.addParameter("--progress");
  }


  private String getRefSpec() {
    AgentSettings s = getSettings();
    if (myMirror) {
      //do fetch into branches with same spec as in remote repository.
      return "+" + GitUtils.expandRef(s.getRef()) + ":" + GitUtils.expandRef(s.getRef());
    } else {
      return "+" + GitUtils.expandRef(s.getRef()) + ":" + GitUtils.createRemoteRef(s.getRef());
    }
  }

}
