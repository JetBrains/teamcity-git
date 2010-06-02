/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * The command that requires information from vcs root
 */
public class RepositoryCommand extends BaseCommand {
  /**
   * The settings from vcs root
   */
  private final AgentSettings mySettings;


  /**
   * The constructor
   *
   * @param mySettings the settings object
   */
  public RepositoryCommand(AgentSettings mySettings) {
    super(mySettings.getGitCommandPath(), mySettings.getLocalRepositoryDir().getPath());
    this.mySettings = mySettings;
  }

  public RepositoryCommand(AgentSettings mySettings, String workDirectory) {
    super(mySettings.getGitCommandPath(), workDirectory);
    this.mySettings = mySettings;
  }

  /**
   * @return get settings object
   */
  public AgentSettings getSettings() {
    return mySettings;
  }

  /**
   * SSH handler implementation
   */
  class SshHandler implements GitSSHService.Handler {
    /**
     * The handler number
     */
    private final int myHandlerNo;
    /**
     * SSH service
     */
    private final GitSSHService mySsh;

    /**
     * The constructor that registers the handler in the SSH service and command line
     *
     * @param ssh the SSH service
     * @param cmd the command line to register with
     * @throws VcsException if there is a problem with registering the handler
     */
    public SshHandler(GitSSHService ssh, GeneralCommandLine cmd) throws VcsException {
      mySsh = ssh;
      Map<String, String> env = new HashMap<String, String>(System.getenv());
      env.put(GitSSHHandler.SSH_PORT_ENV, Integer.toString(mySsh.getXmlRcpPort()));
      if (mySettings.isKnownHostsIgnored()) {
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

    /**
     * {@inheritDoc}
     */
    public String askPassphrase(String username, String keyPath, boolean resetPassword, String lastError) {
      if (resetPassword) {
        return null;
      }
      return mySettings.getPassphrase();
    }

    /**
     * {@inheritDoc}
     */
    public Vector<String> replyToChallenge(String username,
                                           String name,
                                           String instruction,
                                           int numPrompts,
                                           Vector<String> prompt,
                                           Vector<Boolean> echo,
                                           String lastError) {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public String askPassword(String username, boolean resetPassword, String lastError) {
      // The password is injected into URL
      return null;
    }
  }
}
