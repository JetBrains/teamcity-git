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

import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSch;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;
import org.jetbrains.git4idea.util.ScriptGenerator;

import java.io.File;
import java.io.IOException;

/**
 * SSH service implementation for TeamCity agents
 */
public class GitAgentSSHService extends GitSSHService {

  private final static Logger LOG = Logger.getLogger(GitAgentSSHService.class);

  private final static String JSCH_SSH_LIB = "jsch";
  private final static String TRILEAD_SSH_LIB = "trilead";
  private final static String DEFAULT_SSH_LIB = JSCH_SSH_LIB;

  private final BuildAgent myAgent;
  private final BuildAgentConfiguration myAgentConfiguration;
  private final PluginDescriptor myPluginDescriptor;
  private final VcsRootSshKeyManagerProvider mySshKeyManagerProvider;
  private final CurrentBuildTracker myBuildTracker;
  private String mySshLib;

  public GitAgentSSHService(BuildAgent agent,
                            BuildAgentConfiguration agentConfiguration,
                            PluginDescriptor pluginDescriptor,
                            @NotNull VcsRootSshKeyManagerProvider sshKeyManagerProvider,
                            @NotNull CurrentBuildTracker buildTracker) {
    myAgent = agent;
    myAgentConfiguration = agentConfiguration;
    myPluginDescriptor = pluginDescriptor;
    mySshKeyManagerProvider = sshKeyManagerProvider;
    myBuildTracker = buildTracker;
  }


  @NotNull
  public synchronized String getScriptPath() throws IOException {
    String lib = getSshLib();
    if (!lib.equals(mySshLib) && (myScript != null || myScriptPath != null)) {
      //reset script when ssh lib changes
      if (myScript != null)
        FileUtil.delete(myScript);
      myScript = null;
      myScriptPath = null;
    }
    mySshLib = lib;

    if (TRILEAD_SSH_LIB.equals(lib)) {
      return super.getScriptPath();
    }
    if (JSCH_SSH_LIB.equals(lib)) {
      if (myScript == null || myScriptPath == null || !myScript.exists()) {
        ScriptGenerator generator = new ScriptGenerator(GitSSHHandler.GIT_SSH_PREFIX, JSchClient.class, getTempDir());
        generator.addClasses(JSch.class);
        generator.addClasses(GitUtils.class);
        generator.addClasses(NotNull.class);
        generator.addClasses(GitSSHHandler.class);
        generator.addClasses(VcsException.class);
        myScript = generator.generate();
        myScriptPath = myScript.getCanonicalPath();
        if (SystemInfo.isWindows && myScriptPath.contains(" ")) {
          myScriptPath = GitUtils.getShortFileName(myScript);
        }
      }
      return myScriptPath;
    }
    throw new IllegalStateException("Unknown ssh library '" + lib + "'");
  }

  @NotNull
  private String getSshLib() {
    String lib;
    try {
      AgentRunningBuild build = myBuildTracker.getCurrentBuild();
      lib = build.getSharedConfigParameters().get("teamcity.git.agentSshLib");
    } catch (NoRunningBuildException e) {
      lib = myAgentConfiguration.getConfigurationParameters().get("teamcity.git.agentSshLib");
    }
    return StringUtil.isEmptyOrSpaces(lib) ? DEFAULT_SSH_LIB : lib.trim();
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
