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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author dmitry.neverov
 */
public class GitFactoryImpl implements GitFactory {

  private final GitAgentSSHService mySsh;
  private final AgentPluginConfig myPluginConfig;

  public GitFactoryImpl(@NotNull GitAgentSSHService ssh, @NotNull AgentPluginConfig pluginConfig) {
    mySsh = ssh;
    myPluginConfig = pluginConfig;
  }


  @NotNull
  public GitFacade create(@NotNull File repositoryDir) {
    return new NativeGitFacade(mySsh, myPluginConfig.getPathToGit(), repositoryDir, myPluginConfig.isDeleteTempFiles());
  }
}
