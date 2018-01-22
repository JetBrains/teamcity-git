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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

/**
 * @author dmitry.neverov
 */
public final class PluginConfigFactoryImpl implements PluginConfigFactory {

  private final BuildAgentConfiguration myAgentConfig;
  private final GitDetector myGitDetector;

  public PluginConfigFactoryImpl(BuildAgentConfiguration agentConfig, GitDetector gitDetector) {
    myAgentConfig = agentConfig;
    myGitDetector = gitDetector;
  }


  public AgentPluginConfig createConfig(AgentRunningBuild build, VcsRoot root) throws VcsException {
    GitExec gitExec = myGitDetector.getGitPathAndVersion(root, myAgentConfig, build);
    return new PluginConfigImpl(myAgentConfig, build, gitExec);
  }

}
