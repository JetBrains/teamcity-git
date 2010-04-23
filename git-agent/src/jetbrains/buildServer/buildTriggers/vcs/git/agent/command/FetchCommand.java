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
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * The "git fetch" command
 */
public class FetchCommand extends BaseCommand {
  /**
   * The fetch timeout (24 hours)
   */
  static final int TIMEOUT = 24 * 60 * 60;
  /**
   * Agent's SSH service
   */
  private final GitAgentSSHService mySsh;

  /**
   * The constructor
   *
   * @param settings the settings object
   */
  public FetchCommand(@NotNull final Settings settings, @NotNull final GitAgentSSHService ssh) {
    super(settings);
    mySsh = ssh;
  }

  /**
   * Perform fetch operation according to settings
   *
   * @param firstFetch true, if the fetch is known to be a first fetch
   * @throws VcsException the VCS exception
   */
  public void fetch(boolean firstFetch) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("fetch");
    Settings s = getSettings();
    Integer depth = s.getAgentHistoryDepth();
    if (depth != null && firstFetch) {
      cmd.addParameter("--depth=" + depth);
    }
    cmd.addParameters("--no-tags", "-q", "origin",
                      "+" + GitUtils.branchRef(s.getBranch()) + ":" + GitUtils.remotesBranchRef(s.getBranch()));
    SshHandler h = new SshHandler(mySsh, cmd);
    try {
      runCommand(cmd, TIMEOUT);
    } finally {
      h.unregister();
    }
  }

}
