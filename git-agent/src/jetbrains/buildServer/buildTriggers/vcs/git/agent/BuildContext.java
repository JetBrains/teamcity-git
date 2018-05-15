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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildInterruptReason;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildContext implements Context {

  private final AgentRunningBuild myBuild;
  private final AgentPluginConfig myConfig;

  public BuildContext(@NotNull AgentRunningBuild build,
                      @NotNull AgentPluginConfig config) {
    myBuild = build;
    myConfig = config;
  }

  @Nullable
  public BuildInterruptReason getInterruptionReason() {
    return myBuild.getInterruptReason();
  }

  @Nullable
  @Override
  public String getSshMacType() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.sshMacType");
    if (!StringUtil.isEmpty(value))
      return value;
    return null;
  }

  @Nullable
  @Override
  public String getPreferredSshAuthMethods() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.sshPreferredAuthMethods");
    if (!StringUtil.isEmpty(value))
      return value;
    return "publickey,keyboard-interactive,password";
  }

  @Override
  public boolean isProvideCredHelper() {
    return myConfig.isProvideCredHelper();
  }

  @Override
  public boolean isDebugSsh() {
    return Loggers.VCS.isDebugEnabled() || Boolean.parseBoolean(myBuild.getSharedConfigParameters().get("teamcity.git.sshDebug"));
  }

  @Nullable
  @Override
  public AgentPluginConfig getConfig() {
    return myConfig;
  }
}
