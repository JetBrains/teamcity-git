/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

  /**
   * The constructor that registers the handler in the SSH service and command line
   *
   * @param ssh the SSH service
   * @param authSettings authentication settings
   * @param cmd the command line to register with
   * @throws VcsException if there is a problem with registering the handler
   */
  public SshHandler(GitSSHService ssh, AuthSettings authSettings, GeneralCommandLine cmd) throws VcsException {
    mySsh = ssh;
    myAuthSettings = authSettings;
    Map<String, String> env = new HashMap<String, String>(System.getenv());
    env.put(GitSSHHandler.SSH_PORT_ENV, Integer.toString(mySsh.getXmlRcpPort()));
    if (myAuthSettings.isIgnoreKnownHosts()) {
      env.put(GitSSHHandler.SSH_IGNORE_KNOWN_HOSTS_ENV, "true");
    }
    try {
      env.put(GitSSHHandler.GIT_SSH_ENV, ssh.getScriptPath().toString());
    } catch (IOException e) {
      throw new VcsException("SSH script cannot be generated", e);
    }
    myHandlerNo = ssh.registerHandler(this);
    env.put(GitSSHHandler.SSH_HANDLER_ENV, Integer.toString(myHandlerNo));
    cmd.setEnvParams(env);
  }

  /**
   * Unregister the handler
   */
  public void unregister() {
    mySsh.unregisterHandler(myHandlerNo);
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
}
