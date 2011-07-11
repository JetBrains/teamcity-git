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
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.vcs.AgentVcsSupport;
import jetbrains.buildServer.agent.vcs.UpdateByCheckoutRules2;
import jetbrains.buildServer.agent.vcs.UpdatePolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules2 {

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final GitAgentSSHService mySshService;
  private final PluginConfigFactory myConfigFactory;

  public GitAgentVcsSupport(SmartDirectoryCleaner directoryCleaner,
                            GitAgentSSHService sshService,
                            PluginConfigFactory configFactory) {
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myConfigFactory = configFactory;
  }


  @NotNull
  @Override
  public UpdatePolicy getUpdatePolicy() {
    return this;
  }


  @NotNull
  @Override
  public String getName() {
    return Constants.VCS_NAME;
  }


  public void updateSources(@NotNull VcsRoot root,
                            @NotNull CheckoutRules checkoutRules,
                            @NotNull String toVersion,
                            @NotNull File checkoutDirectory,
                            @NotNull AgentRunningBuild build,
                            boolean cleanCheckoutRequested) throws VcsException {
    AgentPluginConfig config = myConfigFactory.createConfig(build, root);
    new GitCommandUpdateProcess(myDirectoryCleaner,
                                mySshService,
                                root,
                                checkoutRules,
                                toVersion,
                                checkoutDirectory,
                                build,
                                config).updateSources();
  }
}
