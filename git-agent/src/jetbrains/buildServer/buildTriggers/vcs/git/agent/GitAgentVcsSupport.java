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
  /**
   * The configuration for the agent
   */
  final BuildAgentConfiguration myAgentConfiguration;
  /**
   * The directory cleaner instance
   */
  final SmartDirectoryCleaner myDirectoryCleaner;
  /**
   * The ssh service to use
   */
  final GitAgentSSHService mySshService;
  final private GitDetector myGitDetector;

  /**
   * The constructor
   *
   * @param agentConfiguration the configuration for this agent
   * @param directoryCleaner   the directory cleaner
   * @param sshService         the used ssh service
   * @param gitDetector        detector of path to git on agent
   */
  public GitAgentVcsSupport(BuildAgentConfiguration agentConfiguration,
                            SmartDirectoryCleaner directoryCleaner,
                            GitAgentSSHService sshService,
                            GitDetector gitDetector) {
    myAgentConfiguration = agentConfiguration;
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myGitDetector = gitDetector;
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
    String pathToGit = myGitDetector.getPathToGit(root, myAgentConfiguration, build);
    new GitCommandUpdateProcess(myAgentConfiguration,
                                myDirectoryCleaner,
                                mySshService,
                                pathToGit,
                                root,
                                checkoutRules,
                                toVersion,
                                checkoutDirectory,
                                build).updateSources();
  }
}
