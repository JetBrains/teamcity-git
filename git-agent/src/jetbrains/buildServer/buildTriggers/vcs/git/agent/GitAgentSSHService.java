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
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSch;
import com.jcraft.jzlib.JZlib;
import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import jetbrains.buildServer.vcs.VcsException;
import org.bouncycastle.asn1.smime.SMIMEAttributes;
import org.bouncycastle.crypto.CipherParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;
import org.jetbrains.git4idea.util.ScriptGenerator;

/**
 * SSH service implementation for TeamCity agents
 */
public class GitAgentSSHService extends GitSSHService {

  private final static String JSCH_SSH_LIB = "jsch";
  private final static String DEFAULT_SSH_LIB = JSCH_SSH_LIB;

  private final BuildAgentConfiguration myAgentConfiguration;
  private final VcsRootSshKeyManagerProvider mySshKeyManagerProvider;

  public GitAgentSSHService(BuildAgentConfiguration agentConfiguration,
                            @NotNull VcsRootSshKeyManagerProvider sshKeyManagerProvider) {
    myAgentConfiguration = agentConfiguration;
    mySshKeyManagerProvider = sshKeyManagerProvider;
  }


  @NotNull
  public synchronized String getScriptPath() throws IOException {
    if (myScript == null || myScriptPath == null || !myScript.exists()) {
      ScriptGenerator generator = new ScriptGenerator(GitSSHHandler.GIT_SSH_PREFIX, JSchClient.class, getTempDir());
      generator.addClasses(JSch.class);
      generator.addClasses(CipherParameters.class);
      generator.addClasses(SMIMEAttributes.class);
      generator.addClasses(JZlib.class);
      generator.addClasses(GitUtils.class);
      generator.addClasses(NotNull.class);
      generator.addClasses(GitSSHHandler.class);
      generator.addClasses(VcsException.class);
      generator.addClasses(JSchConfigInitializer.class);
      generator.addClasses(Pair.class); // JSchConfigInitializer depends on it
      generator.addClasses(MapSerializerUtil.class); // SshPubkeyAcceptedAlgorithms depends on it via StringUtil
      myScript = generator.generate();
      myScriptPath = myScript.getCanonicalPath();
      if (SystemInfo.isWindows && myScriptPath.contains(" ")) {
        myScriptPath = GitUtils.getShortFileName(myScript);
      }
    }
    return myScriptPath;
  }

  @Override
  protected File getTempDir() {
    return myAgentConfiguration.getTempDirectory();
  }

  @Override
  public int getXmlRcpPort() {
    return myAgentConfiguration.getOwnPort();
  }

  @Nullable
  public VcsRootSshKeyManager getSshKeyManager() {
    return mySshKeyManagerProvider.getSshKeyManager();
  }
}
