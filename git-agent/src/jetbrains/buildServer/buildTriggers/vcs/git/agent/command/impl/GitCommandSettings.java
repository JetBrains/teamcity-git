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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCommandSettings {

  private Integer myTimeout = CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
  private AuthSettings myAuthSettings;
  private boolean myUseNativeSsh = false;

  public static GitCommandSettings with() {
    return new GitCommandSettings();
  }

  public GitCommandSettings timeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  public GitCommandSettings authSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return this;
  }

  public GitCommandSettings useNativeSsh(boolean doUseNativeSsh) {
    myUseNativeSsh = doUseNativeSsh;
    return this;
  }

  public int getTimeout() {
    return myTimeout;
  }

  @Nullable
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  public boolean isUseNativeSsh() {
    return myUseNativeSsh;
  }
}
