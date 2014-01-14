/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.File;
import java.io.IOException;

/**
 * SSH service implementation for TeamCity agents
 */
public class GitAgentSSHService extends GitSSHService {

  private final static Logger LOG = Logger.getLogger(GitAgentSSHService.class);

  private final BuildAgent myAgent;
  private final BuildAgentConfiguration myAgentConfiguration;
  private final PluginDescriptor myPluginDescriptor;
  private final VcsRootSshKeyManagerProvider mySshKeyManagerProvider;


  public GitAgentSSHService(BuildAgent agent,
                            BuildAgentConfiguration agentConfiguration,
                            PluginDescriptor pluginDescriptor,
                            @NotNull VcsRootSshKeyManagerProvider sshKeyManagerProvider) {
    myAgent = agent;
    myAgentConfiguration = agentConfiguration;
    myPluginDescriptor = pluginDescriptor;
    mySshKeyManagerProvider = sshKeyManagerProvider;
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
  public String getSshLibraryPath() throws IOException {
    File sshLibDir = new File(myPluginDescriptor.getPluginRoot(), "lib" + File.separator + "ssh");
    File sshJar = new File(sshLibDir, "trilead-ssh2.jar");
    if (!sshJar.exists()) {
      LOG.warn("Cannot find ssh library jar at " + sshJar.getCanonicalPath());
    }
    return sshJar.getCanonicalPath();
  }

  @Override
  protected void registerInternalHandler(String handlerName, GitSSHHandler handler) {
    myAgent.getXmlRpcHandlerManager().addHandler(handlerName, handler);
  }

  @Nullable
  public VcsRootSshKeyManager getSshKeyManager() {
    return mySshKeyManagerProvider.getSshKeyManager();
  }
}
