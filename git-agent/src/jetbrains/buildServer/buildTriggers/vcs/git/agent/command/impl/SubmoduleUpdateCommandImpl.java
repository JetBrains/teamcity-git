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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class SubmoduleUpdateCommandImpl implements SubmoduleUpdateCommand {

  private final GeneralCommandLine myCmd;
  private final GitAgentSSHService mySsh;
  private boolean myUseNativeSsh;
  private Settings.AuthSettings myAuthSettings;
  private int myTimeout;
  
  public SubmoduleUpdateCommandImpl(@NotNull GeneralCommandLine cmd, @NotNull GitAgentSSHService ssh) {
    myCmd = cmd;
    mySsh = ssh;
  }

  @NotNull
  public SubmoduleUpdateCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  @NotNull
  public SubmoduleUpdateCommand setAuthSettings(@NotNull Settings.AuthSettings settings) {
    myAuthSettings = settings;
    return this;
  }

  @NotNull
  public SubmoduleUpdateCommand setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  public void call() throws VcsException {
    myCmd.addParameter("submodule");
    myCmd.addParameter("update");
    if (myUseNativeSsh) {
      CommandUtil.runCommand(myCmd, myTimeout);
    } else {
      SshHandler h = new SshHandler(mySsh, myAuthSettings, myCmd);
      try {
        CommandUtil.runCommand(myCmd, myTimeout);
      } finally {
        h.unregister();
      }
    }
  }
}
