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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.agent.BuildInterruptReason;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitCommandLine extends GeneralCommandLine {

  private final GitAgentSSHService mySsh;
  private final ScriptGen myScriptGen;
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();
  private final File myTmpDir;
  private final boolean myDeleteTempFiles;
  private final GitProgressLogger myLogger;
  private final GitVersion myGitVersion;
  private final Context myCtx;
  private File myWorkingDirectory;
  private boolean myRepeatOnEmptyOutput = false;
  private VcsRootSshKeyManager mySshKeyManager;
  private boolean myHasProgress = false;
  private boolean myUseGitSshCommand = true;

  public GitCommandLine(@Nullable GitAgentSSHService ssh,
                        @NotNull ScriptGen scriptGen,
                        @NotNull File tmpDir,
                        boolean deleteTempFiles,
                        @NotNull GitProgressLogger logger,
                        @NotNull GitVersion gitVersion,
                        @NotNull Map<String, String> env,
                        @NotNull Context ctx) {
    mySsh = ssh;
    myScriptGen = scriptGen;
    myTmpDir = tmpDir;
    myDeleteTempFiles = deleteTempFiles;
    myLogger = logger;
    myGitVersion = gitVersion;
    myCtx = ctx;
    setPassParentEnvs(true);
    setEnvParams(env);
  }

  public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
    AuthSettings authSettings = settings.getAuthSettings();
    if (myCtx.isProvideCredHelper() && !getParametersList().getParametersString().contains("credential.helper") && !myGitVersion.isLessThan(UpdaterImpl.EMPTY_CRED_HELPER)) {
      //Disable credential helper if it wasn't specified by us, as default
      //helper can require a user input. Do that even if our repository doesn't
      //require any auth, auth can be required by submodules or git lfs.
      //
      //It would be cleaner to add the following
      //
      //[credential]
      //  helper =
      //
      //to the local repository config and not disable helpers in every command.
      //But some commands ignore this setting, e.g. 'git submodules update':
      //https://public-inbox.org/git/CAC+L6n0YeX_n_AysCLtBWkA+jPHwg7HmOWq2PLj75byxOZE=qQ@mail.gmail.com/
      getParametersList().addAt(0, "-c");
      getParametersList().addAt(1, "credential.helper=");
    }
    if (authSettings != null) {
      if (mySsh == null)
        throw new IllegalStateException("Ssh is not initialized");
      if (authSettings.getAuthMethod() == AuthenticationMethod.PASSWORD) {
        try {
          final File askPass = myScriptGen.generateAskPass(authSettings);
          String askPassPath = askPass.getAbsolutePath();
          if (askPassPath.contains(" ") && SystemInfo.isWindows) {
            askPassPath = GitUtils.getShortFileName(askPass);
          }
          getParametersList().addAt(0, "-c");
          getParametersList().addAt(1, "core.askpass=" + askPassPath);
          addPostAction(new Runnable() {
            public void run() {
              if (myDeleteTempFiles)
                FileUtil.delete(askPass);
            }
          });
          addEnvParam("GIT_ASKPASS", askPassPath);
        } catch (IOException e) {
          throw new VcsException(e);
        }
      }
      if (settings.isUseNativeSsh()) {
        if (!myGitVersion.isLessThan(UpdaterImpl.MIN_GIT_SSH_COMMAND) && authSettings.getAuthMethod() == AuthenticationMethod.TEAMCITY_SSH_KEY && myUseGitSshCommand) {
          configureGitSshCommand(authSettings);
        }
        return CommandUtil.runCommand(this, settings.getTimeout());
      } else {
        SshHandler h = new SshHandler(mySsh, mySshKeyManager, authSettings, this, myTmpDir, myCtx);
        try {
          return CommandUtil.runCommand(this, settings.getTimeout());
        } finally {
          h.unregister();
        }
      }
    } else {
      return CommandUtil.runCommand(this, settings.getTimeout());
    }
  }


  private void configureGitSshCommand(@NotNull AuthSettings authSettings) throws VcsException {
    //Git has 2 environment variables related to ssh: GIT_SSH and GIT_SSH_COMMAND.
    //We use GIT_SSH_COMMAND because git resolves the executable specified in it,
    //i.e. it finds the 'ssh' executable which is not in the PATH on windows be default.

    //We specify the following command:
    //
    //  GIT_SSH_COMMAND=ssh -i "<path to decrypted key>" (-o "StrictHostKeyChecking=no")
    //
    //The key is decrypted by us because on MacOS ssh seems to ignore the SSH_ASKPASS and
    //runs the MacOS graphical keychain helper. Disabling it via the -o "KeychainIntegration=no"
    //option results in the 'Bad configuration option: keychainintegration' error.
    File privateKey = null;
    try {
      String keyId = authSettings.getTeamCitySshKeyId();
      if (keyId != null && mySshKeyManager != null) {
        VcsRoot root = authSettings.getRoot();
        if (root != null) {
          TeamCitySshKey key = mySshKeyManager.getKey(root);
          if (key != null) {
            privateKey = FileUtil.createTempFile(myTmpDir, "key", "", true);
            final File finalPrivateKey = privateKey;
            addPostAction(new Runnable() {
              @Override
              public void run() {
                FileUtil.delete(finalPrivateKey);
              }
            });
            FileUtil.writeFileAndReportErrors(privateKey, new String(key.getPrivateKey()));
            KeyPair keyPair = KeyPair.load(new JSch(), privateKey.getAbsolutePath());
            OutputStream out = null;
            try {
              out = new BufferedOutputStream(new FileOutputStream(privateKey));
              if (key.isEncrypted() && !keyPair.decrypt(authSettings.getPassphrase())) {
                throw new VcsException("Wrong SSH key passphrase");
              }
              keyPair.writePrivateKey(out, null);
            } finally {
              FileUtil.close(out);
            }
            //set permissions to 600, without that ssh client rejects the key on *nix
            privateKey.setReadable(false, false);
            privateKey.setReadable(true, true);
            privateKey.setWritable(false, false);
            privateKey.setWritable(true, true);

            String privateKeyPath = privateKey.getAbsolutePath().replace('\\', '/');

            StringBuilder gitSshCommand = new StringBuilder();
            gitSshCommand.append("ssh -i \"").append(privateKeyPath).append("\"");
            if (authSettings.isIgnoreKnownHosts()) {
              gitSshCommand.append(" -o \"StrictHostKeyChecking=no\"");
            }
            addEnvParam("GIT_SSH_COMMAND", gitSshCommand.toString());
          }
        }
      }
    } catch (Exception e) {
      if (privateKey != null)
        FileUtil.delete(privateKey);
      if (e instanceof VcsException)
        throw (VcsException) e;
      throw new VcsException(e);
    }
  }


  @NotNull
  public GitVersion getGitVersion() {
    return myGitVersion;
  }

  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }

  public List<Runnable> getPostActions() {
    return myPostActions;
  }

  @Override
  public void setWorkingDirectory(File workingDirectory) {
    myWorkingDirectory = workingDirectory;
    super.setWorkingDirectory(workingDirectory);
  }

  @Nullable
  public File getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public GitCommandLine repeatOnEmptyOutput(boolean doRepeat) {
    myRepeatOnEmptyOutput = doRepeat;
    return this;
  }

  public boolean isRepeatOnEmptyOutput() {
    return myRepeatOnEmptyOutput;
  }

  public void setSshKeyManager(VcsRootSshKeyManager sshKeyManager) {
    mySshKeyManager = sshKeyManager;
  }

  public void setUseGitSshCommand(boolean useGitSshCommand) {
    myUseGitSshCommand = useGitSshCommand;
  }

  public void addEnvParam(@NotNull String name, @NotNull String value) {
    Map<String, String> existing = getEnvParams();
    if (existing == null)
      existing = new HashMap<String, String>();
    Map<String, String> newParams = new HashMap<String, String>(existing);
    newParams.put(name, value);
    setEnvParams(newParams);
  }

  @NotNull
  public ByteArrayOutputStream createStderrBuffer() {
    LineAwareByteArrayOutputStream buffer = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new GitProgressListener(myLogger));
    buffer.setCREndsLine(true);
    return buffer;
  }

  public void logStart(@NotNull String msg) {
    if (myHasProgress) {
      myLogger.openBlock(msg);
    } else {
      myLogger.message(msg);
    }
  }

  public void logFinish(@NotNull String msg) {
    if (myHasProgress)
      myLogger.closeBlock(msg);
  }

  public void setHasProgress(final boolean hasProgress) {
    myHasProgress = hasProgress;
  }


  public void checkCanceled() throws VcsException {
    BuildInterruptReason reason = myCtx.getInterruptionReason();
    if (reason != null)
      throw new CheckoutCanceledException(reason);
  }

  @Override
  public Charset getCharset() {
    // Override the method instead of using the setCharset(), because
    // setCharset() adds the '-Dfile.encoding={charset}' parameter to
    // the parameter list which makes git to fail.
    AgentPluginConfig config = myCtx.getConfig();
    if (config == null)
      return super.getCharset();
    String charsetName = config.getGitOutputCharsetName();
    if (charsetName == null)
      return super.getCharset();
    try {
      return Charset.forName(charsetName);
    } catch (UnsupportedCharsetException e) {
      return super.getCharset();
    }
  }
}
