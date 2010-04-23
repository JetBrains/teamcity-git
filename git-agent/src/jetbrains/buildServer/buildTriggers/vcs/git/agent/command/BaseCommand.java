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
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * The base class for git commands.
 *
 * @author pavel
 */
public class BaseCommand {
  private final Settings mySettings;
  private String myWorkDirectory;

  public BaseCommand(@NotNull final Settings settings) {
    mySettings = settings;
    myWorkDirectory = settings.getLocalRepositoryDir().getAbsolutePath();
  }

  public Settings getSettings() {
    return mySettings;
  }

  /**
   * Sets new working directory, by default working directory is taken from the Settings#getLocalRepositoryDir
   *
   * @param workDirectory work dir
   */
  public void setWorkDirectory(final String workDirectory) {
    myWorkDirectory = workDirectory;
  }

  protected GeneralCommandLine createCommandLine() {
    GeneralCommandLine cli = new GeneralCommandLine();
    cli.setExePath(getSettings().getGitCommandPath());
    cli.setWorkDirectory(myWorkDirectory);
    return cli;
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli) throws VcsException {
    return CommandUtil.runCommand(cli);
  }

  protected ExecResult runCommand(@NotNull GeneralCommandLine cli, int executionTimeout) throws VcsException {
    return CommandUtil.runCommand(cli, executionTimeout);
  }

  protected void failIfNotEmptyStdErr(@NotNull GeneralCommandLine cli, @NotNull ExecResult res) throws VcsException {
    if (!StringUtil.isEmpty(res.getStderr())) {
      CommandUtil.commandFailed(cli.getCommandLineString(), res);
    }
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
      return !mySettings.isIgnoreKnownHosts();
    }

    public String askPassphrase(String username, String keyPath, boolean resetPassword, String lastError) {
      if (resetPassword) {
        return null;
      }
      return mySettings.getPassphrase();
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
      return mySettings.getPassword();
    }
  }
}
