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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateIndexCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings.with;

public class UpdateIndexCommandImpl extends BaseCommandImpl implements UpdateIndexCommand {

  private boolean myUseNativeSsh;
  private AuthSettings myAuthSettings;
  private boolean myReallyRefresh;
  private boolean myQuiet;

  public UpdateIndexCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }


  @NotNull
  @Override
  public UpdateIndexCommand setAuthSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return this;
  }


  @NotNull
  @Override
  public UpdateIndexCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }


  @NotNull
  @Override
  public UpdateIndexCommand reallyRefresh(final boolean reallyRefresh) {
    myReallyRefresh = reallyRefresh;
    return this;
  }


  @NotNull
  @Override
  public UpdateIndexCommand quiet(final boolean quiet) {
    myQuiet = quiet;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("update-index");
    if (myQuiet)
      cmd.addParameter("-q");
    if (myReallyRefresh)
      cmd.addParameter("--really-refresh");
    cmd.run(with().authSettings(myAuthSettings).useNativeSsh(myUseNativeSsh));
  }
}
