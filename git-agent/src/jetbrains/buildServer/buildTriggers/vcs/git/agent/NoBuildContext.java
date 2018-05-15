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

import jetbrains.buildServer.agent.BuildInterruptReason;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.Nullable;

public class NoBuildContext implements Context {

  @Nullable
  @Override
  public BuildInterruptReason getInterruptionReason() {
    return null;
  }

  @Nullable
  @Override
  public String getSshMacType() {
    return null;
  }

  @Nullable
  @Override
  public String getPreferredSshAuthMethods() {
    return null;
  }

  @Override
  public boolean isProvideCredHelper() {
    return false;
  }

  @Override
  public boolean isDebugSsh() {
    return Loggers.VCS.isDebugEnabled();
  }

  @Nullable
  @Override
  public AgentPluginConfig getConfig() {
    return null;
  }
}
