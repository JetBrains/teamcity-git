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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.version.ServerVersionHolder;
import jetbrains.buildServer.version.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * SSH handler implementation
 */
public class SshHandler implements GitSSHService.Handler {
  /**
   * The handler number
   */
  private final int myHandlerNo;
  /**
   * SSH service
   */
  private final GitSSHService mySsh;
  private final AuthSettings myAuthSettings;
  private final List<File> myFilesToClean = new ArrayList<File>();

  /**
   * The constructor that registers the handler in the SSH service and command line
   *
   * @param ssh the SSH service
   * @param authSettings authentication settings
   * @param cmd the command line to register with
   * @throws VcsException if there is a problem with registering the handler
   */
  public SshHandler(@NotNull GitSSHService ssh,
                    @Nullable VcsRootSshKeyManager sshKeyManager,
                    @NotNull AuthSettings authSettings,
                    @NotNull GitCommandLine cmd,
                    @NotNull File tmpDir,
                    @NotNull Context ctx) throws VcsException {
    mySsh = ssh;
    myAuthSettings = authSettings;
    cmd.addEnvParam(GitSSHHandler.SSH_PORT_ENV, Integer.toString(mySsh.getXmlRcpPort()));
    if (myAuthSettings.isIgnoreKnownHosts())
      cmd.addEnvParam(GitSSHHandler.SSH_IGNORE_KNOWN_HOSTS_ENV, "true");
    if (authSettings.getAuthMethod() == AuthenticationMethod.TEAMCITY_SSH_KEY) {
      String keyId = authSettings.getTeamCitySshKeyId();
      if (keyId != null && sshKeyManager != null) {
        VcsRoot root = myAuthSettings.getRoot();
        if (root != null) {
          TeamCitySshKey key = sshKeyManager.getKey(root);
          if (key != null) {
            try {
              File privateKey = FileUtil.createTempFile(tmpDir, "key", "", true);
              myFilesToClean.add(privateKey);
              FileUtil.writeFileAndReportErrors(privateKey, new String(key.getPrivateKey()));
              cmd.addEnvParam(GitSSHHandler.TEAMCITY_PRIVATE_KEY_PATH, privateKey.getCanonicalPath());
              String passphrase = myAuthSettings.getPassphrase();
              cmd.addEnvParam(GitSSHHandler.TEAMCITY_PASSPHRASE, passphrase != null ? passphrase : "");
            } catch (Exception e) {
              deleteKeys();
              throw new VcsException(e);
            }
          }
        }
      }
    }
    if (ctx.getSshMacType() != null)
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_MAC_TYPE, ctx.getSshMacType());
    if (ctx.getPreferredSshAuthMethods() != null)
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_PREFERRED_AUTH_METHODS, ctx.getPreferredSshAuthMethods());
    cmd.addEnvParam(GitSSHHandler.TEAMCITY_DEBUG_SSH, String.valueOf(ctx.isDebugSsh()));
    AgentPluginConfig config = ctx.getConfig();
    if (config != null)
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_IDLE_TIMEOUT_SECONDS, String.valueOf(config.getIdleTimeoutSeconds()));
    String teamCityVersion = getTeamCityVersion();
    if (teamCityVersion != null) {
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_VERSION, teamCityVersion);
    }
    try {
      cmd.addEnvParam(GitSSHHandler.GIT_SSH_ENV, ssh.getScriptPath());
      // ask git to treat our command as OpenSSH compatible:
      cmd.addEnvParam(GitSSHHandler.GIT_SSH_VARIANT_ENV, "ssh");
    } catch (IOException e) {
      deleteKeys();
      throw new VcsException("SSH script cannot be generated", e);
    }
    myHandlerNo = ssh.registerHandler(this);
    cmd.addEnvParam(GitSSHHandler.SSH_HANDLER_ENV, Integer.toString(myHandlerNo));
  }

  /**
   * Unregister the handler
   */
  public void unregister() {
    deleteKeys();
    mySsh.unregisterHandler(myHandlerNo);
  }


  private void deleteKeys() {
    for (File f : myFilesToClean) {
      FileUtil.delete(f);
    }
  }

  public boolean verifyServerHostKey(String hostname,
                                     int port,
                                     String serverHostKeyAlgorithm,
                                     String serverHostKey,
                                     boolean isNew) {
    return false;
  }

  public String askPassphrase(String username, String keyPath, boolean resetPassword, String lastError) {
    if (resetPassword) {
      return null;
    }
    return myAuthSettings.getPassphrase();
  }

  public Vector<String> replyToChallenge(String username,
                                         String name,
                                         String instruction,
                                         int numPrompts,
                                         Vector<String> prompt,
                                         Vector<Boolean> echo,
                                         String lastError) {
    return null;
  }

  public String askPassword(String username, boolean resetPassword, String lastError) {
    // The password is injected into URL
    return null;
  }

  @Nullable
  private static String getTeamCityVersion() {
    try {
      ServerVersionInfo version = ServerVersionHolder.getVersion();
      return "TeamCity Agent " + version.getDisplayVersion();
    } catch (Exception e) {
      return null;
    }
  }
}
