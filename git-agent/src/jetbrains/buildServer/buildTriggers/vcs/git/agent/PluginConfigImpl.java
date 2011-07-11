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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements AgentPluginConfig {

  private final BuildAgentConfiguration myAgentConfig;
  private final AgentRunningBuild myBuild;
  private final String myPathToGit;
  private final GitVersion myGitVersion;


  public PluginConfigImpl(@NotNull BuildAgentConfiguration agentConfig,
                          @NotNull AgentRunningBuild build,
                          @NotNull String pathToGit,
                          @NotNull GitVersion version) {
    myAgentConfig = agentConfig;
    myBuild = build;
    myPathToGit = pathToGit;
    myGitVersion = version;
  }


  @NotNull
  public File getCachesDir() {
    return myAgentConfig.getCacheDirectory("git");
  }


  public int getIdleTimeoutSeconds() {
    String valueFromBuild = myBuild.getSharedConfigParameters().get("teamcity.git.idle.timeout.seconds");
    if (valueFromBuild != null)
      return parseTimeout(valueFromBuild);
    else
      return DEFAULT_IDLE_TIMEOUT;
  }


  @NotNull
  public String getPathToGit() {
    return myPathToGit;
  }


  public boolean isUseNativeSSH() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.use.native.ssh");
    return "true".equals(value);
  }


  public boolean isUseLocalMirrors() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.use.local.mirrors");
    return "true".equals(value);
  }


  @NotNull
  public GitVersion getGitVersion() {
    return myGitVersion;
  }

  private int parseTimeout(String valueFromBuild) {
    try {
      int timeout = Integer.parseInt(valueFromBuild);
      if (timeout > 0)
        return timeout;
      else
        return DEFAULT_IDLE_TIMEOUT;
    } catch (NumberFormatException e) {
      return DEFAULT_IDLE_TIMEOUT;
    }
  }
}
