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
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * @author dmitry.neverov
 */
public class FetchCommandImpl implements FetchCommand {

  private final GeneralCommandLine myCmd;
  private final GitAgentSSHService mySsh;
  private final AskPassGenerator myAskPassGenerator;
  private boolean myUseNativeSsh;
  private int myTimeout;
  private String myRefspec;
  private boolean myQuite;
  private boolean myShowProgress;
  private Settings.AuthSettings myAuthSettings;
  private Integer myDepth;

  public FetchCommandImpl(@NotNull GeneralCommandLine cmd, @Nullable GitAgentSSHService ssh, @NotNull AskPassGenerator askPassGenerator) {
    myCmd = cmd;
    mySsh = ssh;
    myAskPassGenerator = askPassGenerator;
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
    if (!myUseNativeSsh && mySsh == null)
      throw new IllegalStateException("Ssh service is not set");

    myCmd.addParameter("fetch");
    if (myQuite)
      myCmd.addParameter("-q");
    if (myShowProgress)
      myCmd.addParameter("--progress");
    if (myDepth != null)
      myCmd.addParameter("--depth=" + myDepth);
    myCmd.addParameter("origin");
    myCmd.addParameter(myRefspec);
    if (myAuthSettings.getAuthMethod() == AuthenticationMethod.PASSWORD) {
      final String askPassScript = myAskPassGenerator.generateScriptFor(myAuthSettings.getPassword());
      myCmd.setEnvParams(new HashMap<String, String>() {{
        put("GIT_ASKPASS", askPassScript);
      }});
    }
    try {
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
    } finally {
      myAskPassGenerator.cleanup();
    }
  }
}
