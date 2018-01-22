/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings.with;

public class SubmoduleUpdateCommandImpl extends BaseCommandImpl implements SubmoduleUpdateCommand {

  private boolean myUseNativeSsh;
  private AuthSettings myAuthSettings;
  private int myTimeout;
  private boolean myForce;

  public SubmoduleUpdateCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public SubmoduleUpdateCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  @NotNull
  public SubmoduleUpdateCommand setAuthSettings(@NotNull AuthSettings settings) {
    myAuthSettings = settings;
    return this;
  }

  @NotNull
  public SubmoduleUpdateCommand setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  @NotNull
  public SubmoduleUpdateCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("submodule");
    cmd.addParameter("update");
    if (myForce)
      cmd.addParameter("--force");
    cmd.run(with().timeout(myTimeout)
              .authSettings(myAuthSettings)
              .useNativeSsh(myUseNativeSsh));
  }
}
