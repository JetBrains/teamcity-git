/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class FetchCommandImpl implements FetchCommand {

  private final GeneralCommandLine myCmd;
  private final GitAgentSSHService mySsh;
  private boolean myUseNativeSsh;
  private int myTimeout;
  private String myRefspec;
  private boolean myQuite;
  private boolean myShowProgress;
  private Settings.AuthSettings myAuthSettings;
  private Integer myDepth;

  public FetchCommandImpl(@NotNull GeneralCommandLine cmd, @NotNull GitAgentSSHService ssh) {
    myCmd = cmd;
    mySsh = ssh;
  }


  @NotNull
  public FetchCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  @NotNull
  public FetchCommand setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  @NotNull
  public FetchCommand setRefspec(@NotNull String refspec) {
    myRefspec = refspec;
    return this;
  }

  @NotNull
  public FetchCommand setQuite(boolean quite) {
    myQuite = quite;
    return this;
  }

  @NotNull
  public FetchCommand setShowProgress(boolean showProgress) {
    myShowProgress = showProgress;
    return this;
  }

  @NotNull
  public FetchCommand setAuthSettings(@NotNull Settings.AuthSettings settings) {
    myAuthSettings = settings;
    return this;
  }

  @NotNull
  public FetchCommand setDepth(int depth) {
    myDepth = depth;
    return this;
  }

  public void call() throws VcsException {
    myCmd.addParameter("fetch");
    if (myQuite)
      myCmd.addParameter("-q");
    if (myShowProgress)
      myCmd.addParameter("--progress");
    if (myDepth != null)
      myCmd.addParameter("--depth=" + myDepth);
    myCmd.addParameter("origin");
    myCmd.addParameter(myRefspec);
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
