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

import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.File;

/**
 * SSH service implementation for TeamCity agents
 */
public class GitAgentSSHService extends GitSSHService {

  /**
   * The configuration for the build agent
   */
  final BuildAgentConfiguration myAgentConfiguration;
  /**
   * The agent service
   */
  final BuildAgent myAgent;

  /**
   * The constructor
   *
   * @param myAgent              the agent to use
   * @param myAgentConfiguration the agent configuration to use
   */
  public GitAgentSSHService(BuildAgent myAgent, BuildAgentConfiguration myAgentConfiguration) {
    this.myAgent = myAgent;
    this.myAgentConfiguration = myAgentConfiguration;
  }

  @Override
  protected File getTempDir() {
    return myAgentConfiguration.getTempDirectory();
  }

  @Override
  public int getXmlRcpPort() {
    return myAgentConfiguration.getOwnPort();
  }

  @Override
  protected void registerInternalHandler(String handlerName, GitSSHHandler handler) {
    myAgent.getXmlRpcHandlerManager().addHandler(handlerName, handler);
  }
}
