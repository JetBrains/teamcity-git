package jetbrains.buildServer.buildTriggers.vcs.git.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.CheckoutCanceledException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitProgressListener;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCommandLine extends GeneralCommandLine {

  protected final Context myCtx;
  private final ScriptGen myScriptGen;
  private final GitProgressLogger myLogger;
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();
  @Nullable protected VcsRootSshKeyManager mySshKeyManager;
  private File myWorkingDirectory;
  private boolean myHasProgress = false;
  private boolean myRepeatOnEmptyOutput = false;
  @Nullable private Integer myMaxOutputSize = null;

  public GitCommandLine(@NotNull Context ctx, @NotNull ScriptGen scriptGen) {
    myCtx = ctx;
    myScriptGen = scriptGen;
    myLogger = myCtx.getLogger();
    setPassParentEnvs(true);
    setEnvParams(myCtx.getEnv());
  }

  @NotNull
  public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
    if (myCtx.isProvideCredHelper() && !getParametersList().getParametersString().contains("credential.helper")) {
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
    final AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings == null) {
      return doRunCommand(settings);
    }
    if (AuthenticationMethod.PASSWORD == authSettings.getAuthMethod()) {
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
            if (myCtx.isDeleteTempFiles())
              FileUtil.delete(askPass);
          }
        });
        addEnvParam("GIT_ASKPASS", askPassPath);
      } catch (IOException e) {
        throw new VcsException(e);
      }
    }
    if (settings.isUseNativeSsh()) {
      if (AuthenticationMethod.TEAMCITY_SSH_KEY == authSettings.getAuthMethod() && myCtx.isUseGitSshCommand()) {
        configureGitSshCommand(settings);
      }
    }
    settings.getTraceEnv().entrySet().forEach(e -> addEnvParam(e.getKey(), e.getValue()));
    return doRunCommand(settings);
  }

  @NotNull
  protected ExecResult doRunCommand(@NotNull GitCommandSettings settings) throws VcsException {
    return CommandUtil.runCommand(this, settings.getTimeout(), settings.getInput());
  }

  private void configureGitSshCommand(@NotNull GitCommandSettings settings) throws VcsException {
    final AuthSettings authSettings = settings.getAuthSettings();
    //Git has 2 environment variables related to ssh: GIT_SSH and GIT_SSH_COMMAND.
    //We use GIT_SSH_COMMAND because git resolves the executable specified in it,
    //i.e. it finds the 'ssh' executable which is not in the PATH on windows by default.

    //We specify the following command:
    //
    //  GIT_SSH_COMMAND=ssh -i "<path to decrypted key>" (-o "StrictHostKeyChecking=no" -vvv)
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
            privateKey = FileUtil.createTempFile(myCtx.getTempDir(), "key", "", true);
            final File finalPrivateKey = privateKey;
            addPostAction(new Runnable() {
              @Override
              public void run() {
                FileUtil.delete(finalPrivateKey);
              }
            });
            FileUtil.writeFileAndReportErrors(privateKey, new String(key.getPrivateKey()));
            final KeyPair keyPair = KeyPair.load(new JSch(), privateKey.getAbsolutePath());
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
            if (myCtx.isDebugSsh() || settings.isTrace()) {
              gitSshCommand.append("-vvv");
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

  public void checkCanceled() throws VcsException {
    String reason = myCtx.getInterruptionReason();
    if (reason != null)
      throw new CheckoutCanceledException(reason);
  }

  public void addEnvParam(@NotNull String name, @NotNull String value) {
    Map<String, String> existing = getEnvParams();
    if (existing == null)
      existing = new HashMap<String, String>();
    Map<String, String> newParams = new HashMap<String, String>(existing);
    newParams.put(name, value);
    setEnvParams(newParams);
  }

  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }

  public List<Runnable> getPostActions() {
    return myPostActions;
  }

  @Nullable
  public File getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public void setWorkingDirectory(File workingDirectory) {
    myWorkingDirectory = workingDirectory;
    super.setWorkingDirectory(workingDirectory);
  }

  public void setHasProgress(final boolean hasProgress) {
    myHasProgress = hasProgress;
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

  public boolean isRepeatOnEmptyOutput() {
    return myRepeatOnEmptyOutput;
  }

  public GitCommandLine repeatOnEmptyOutput(boolean repeatOnEmptyOutput) {
    myRepeatOnEmptyOutput = repeatOnEmptyOutput;
    return this;
  }

  @NotNull
  public ByteArrayOutputStream createStderrBuffer() {
    LineAwareByteArrayOutputStream buffer = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new GitProgressListener(myLogger));
    buffer.setCREndsLine(true);
    return buffer;
  }

  public GitCommandLine withMaxOutputSize(int maxOutputSize) {
    myMaxOutputSize = maxOutputSize;
    return this;
  }

  @Nullable
  public Integer getMaxOutputSize() {
    return myMaxOutputSize;
  }

  public void setSshKeyManager(VcsRootSshKeyManager sshKeyManager) {
    mySshKeyManager = root -> {
      final TeamCitySshKey key = sshKeyManager.getKey(root);
      if (key == null) {
        throw new IllegalArgumentException("Failed to retrieve uploaded ssh key");
      }
      return key;
    };
  }

  @NotNull
  public GitVersion getGitVersion() {
    return myCtx.getGitVersion();
  }

  @Override
  public Charset getCharset() {
    final Charset charset = myCtx.getCharset();
    return charset == null ? super.getCharset() : charset;
  }
}