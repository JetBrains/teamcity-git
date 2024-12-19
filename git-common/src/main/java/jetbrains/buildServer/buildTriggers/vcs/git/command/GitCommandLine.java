package jetbrains.buildServer.buildTriggers.vcs.git.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelperConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.CheckoutCanceledException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.SshKeyNotFoundException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitProgressListener;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.http.auth.Credentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCommandLine extends GeneralCommandLine {

  protected final Context myCtx;
  private final ScriptGen myScriptGen;
  private final GitProgressLogger myLogger;
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();
  @Nullable protected VcsRootSshKeyManager mySshKeyManager;
  private final ProxyHandler myProxy;
  private File myWorkingDirectory;
  private boolean myHasProgress = false;
  private boolean myRepeatOnEmptyOutput = false;
  @Nullable private Integer myMaxOutputSize = null;

  private boolean myAbnormalExitExpected = false;
  private boolean myStdErrExpected = true;
  private String myStdErrLogLevel = "warn";

  public GitCommandLine(@NotNull Context ctx, @NotNull ScriptGen scriptGen) {
    myCtx = ctx;
    myScriptGen = scriptGen;
    myLogger = myCtx.getLogger();
    setPassParentEnvs(true);
    setEnvParams(myCtx.getEnv());
    myProxy = new ProxyHandler();
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

      //if credential.helper is configured to the empty string, this resets the helper list to empty
      getParametersList().addAt(0, "-c");
      getParametersList().addAt(1, "credential.helper=");

      String credHelperPath = CredentialsHelperConfig.configureCredentialHelperScript(myScriptGen);
      if (!StringUtil.isEmptyOrSpaces(credHelperPath)) {
        getParametersList().addAt(2, "-c");
        getParametersList().addAt(3, "credential.helper=" + credHelperPath);


        if (myCtx.isCleanCredHelperScript()) {
          addPostAction(new Runnable() {
            @Override
            public void run() {
              FileUtil.delete(new File(credHelperPath));
            }
          });
        }
      }
    }

    settings.getTraceEnv().entrySet().forEach(e -> addEnvParam(e.getKey(), e.getValue()));

    setProxySettings();

    final AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings == null) {
      return doRunCommand(settings);
    }
    if (authSettings.getAuthMethod().isPasswordBased()) {
      withAskPassScript(authSettings.getPassword(), askPassPath -> {
        getParametersList().addAt(0, "-c");
        getParametersList().addAt(1, "core.askpass=" + askPassPath);

        addEnvParam("GIT_ASKPASS", askPassPath);
        if (myCtx.isUseSshAskPass()) {
          addEnvParam("SSH_ASKPASS", askPassPath);
          addEnvParam("DISPLAY", ":0.0");
        }
      });
    }
    if (settings.isUseNativeSsh() && myCtx.isUseGitSshCommand()) {
      configureGitSshCommand(settings);
    }

    GitCommandCredentials extraCredentials = authSettings.getExtraHTTPCredentials();
    CredentialsHelperConfig config = new CredentialsHelperConfig();
    for (ExtraHTTPCredentials creds : extraCredentials.getCredentials()) {
      config.addCredentials(creds);
    }

    if (extraCredentials.isStoresOnlyDefaultCredential()) {
      config.setMatchAllUrls(true);
    } else if (extraCredentials.getCredentials().size() > 1){
      getParametersList().addAt(0, "-c");
      getParametersList().addAt(1, "credential.useHttpPath=true");
    }


    for (Map.Entry<String, String> e : config.getEnv().entrySet()) {
      addEnvParam(e.getKey(), e.getValue());
    }

    return doRunCommand(settings);
  }

  private void setProxySettings() {
    if (myProxy.isHttpProxyEnabled()) {
      Credentials credentials = myProxy.getHttpCredentials();

      addEnvParam("http_proxy", getFullProxyAddr(myProxy.getHttpProxyHost(), myProxy.getHttpProxyPort(), credentials));
    }

    if (myProxy.isHttpsProxyEnabled()) {
      Credentials credentials = myProxy.getHttpsCredentials();

      addEnvParam("https_proxy", getFullProxyAddr(myProxy.getHttpsProxyHost(), myProxy.getHttpsProxyPort(), credentials));
    }

    if (myProxy.isHttpProxyEnabled() || myProxy.isHttpsProxyEnabled()) {
      String nonProxyHosts = myProxy.getNonProxyHosts();
      if (nonProxyHosts != null) {
        addEnvParam("no_proxy", convertNonProxyHosts(nonProxyHosts));
      }
    }
  }

  private String getFullProxyAddr(@NotNull String proxyHost, int proxyPort, @Nullable Credentials credentials) {
    if (credentials == null) {
      return proxyPort != 0 ? String.format("%s:%d", proxyHost, proxyPort) : proxyHost;
    } else {
      return proxyPort != 0 ? String.format("%s:%s@%s:%d", credentials.getUserPrincipal().getName(), credentials.getPassword(), proxyHost, proxyPort)
                            : String.format("%s:%s@%s", credentials.getUserPrincipal().getName(), credentials.getPassword(), proxyHost);
    }
  }

  private String convertNonProxyHosts(@NotNull String nonProxyHostsJava) {
    return Arrays.stream(nonProxyHostsJava.split("\\|"))
                 .map(host -> host.trim())
                 .map(host -> host.startsWith("*.") ? host.substring(1) : host)
                 .filter(host -> {
                   if (host.endsWith(".*")) {
                     myLogger.warning("NonProxyHosts pattern '" + host + "' will be ignored because suffix wildcards are not supported in git");
                     return false;
                   }
                   return true;
                 })
      .collect(Collectors.joining(","));
  }

  @NotNull
  protected ExecResult doRunCommand(@NotNull GitCommandSettings settings) throws VcsException {
    return CommandUtil.runCommand(this, settings.getTimeout(), settings.getInput());
  }

  private void configureGitSshCommand(@NotNull GitCommandSettings settings) throws VcsException {
    final AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings == null) return;

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

    final boolean ignoreKnownHosts = isIgnoreKnownHosts(authSettings);
    final String sendEnv = getSshRequestToken();
    File privateKey = null;
    try {
      privateKey = getPrivateKey(authSettings);
      if (privateKey != null || ignoreKnownHosts || StringUtil.isNotEmpty(sendEnv)) {
        final StringBuilder gitSshCommand = new StringBuilder("ssh");
        if (privateKey != null) {
          gitSshCommand.append(" -i \"").append(privateKey.getAbsolutePath().replace('\\', '/')).append("\"");
        }
        if (ignoreKnownHosts) {
          gitSshCommand.append(" -o \"StrictHostKeyChecking=no\" -o \"UserKnownHostsFile=/dev/null\" -o \"GlobalKnownHostsFile=/dev/null\"");
        } else {
          String knownHosts = myCtx.getSshKnownHosts();
          if (knownHosts == null) {
            myLogger.warning(
              "\"Ignore known hosts database\" setting is disabled, please make sure that per-user or global known host key database contains remote host key, otherwise git operations may hang or fail in unexpected way");
          } else {
            File knownHostsFile = FileUtil.createTempFile(myCtx.getTempDir(), "known_hosts", "", true);
            addPostAction(() -> FileUtil.delete(knownHostsFile));
            try (FileWriter writer = new FileWriter(knownHostsFile)) {
              writer.write(knownHosts);
            }
            String knownHostsPath = knownHostsFile.getAbsolutePath();
            gitSshCommand.append(String.format(" -o \"UserKnownHostsFile=%s\" -o \"GlobalKnownHostsFile=%s\"", knownHostsPath, knownHostsPath));
          }

        }

        if (myProxy.isSshProxyEnabled()) {
          int port = myProxy.getSshProxyPort();
          String fullProxyAddr = port != 0 ? String.format("%s:%d", myProxy.getSshProxyHost(), port) : myProxy.getSshProxyHost();
          String socksProxyCommand;
          if (SystemInfo.isWindows) {
            String strType;
            if (myProxy.getSshProxyType() == ProxyHandler.SshProxyType.HTTP) {
              // http proxy
              strType = "H";
            } else {
              // socks proxy
              strType = "S";
            }
            socksProxyCommand = String.format(" -o ProxyCommand=\"connect -%s %s %%h %%p\"", strType, fullProxyAddr);
          } else {
            String strType;
            switch (Objects.requireNonNull(myProxy.getSshProxyType())) {
              case SOCKS4: strType = "4"; break;
              case SOCKS5: strType = "5"; break;
              default: strType = "connect"; break;
            }
            socksProxyCommand = String.format(" -o ProxyCommand=\"nc -v -X %s -x %s %%h %%p\"", strType, fullProxyAddr);
          }
          gitSshCommand.append(socksProxyCommand);
        }

        if (authSettings.getAuthMethod().isKeyAuth()) {
          gitSshCommand.append(" -o \"PreferredAuthentications=publickey\" -o \"PasswordAuthentication=no\" -o \"KbdInteractiveAuthentication=no\"");
        } else {
          gitSshCommand.append(" -o \"PreferredAuthentications=password,keyboard-interactive\" -o \"PubkeyAuthentication=no\"");
        }
        gitSshCommand.append(" -o \"IdentitiesOnly=yes\"");

        if (StringUtil.isNotEmpty(sendEnv)) {
          gitSshCommand.append(" -o \"SetEnv TEAMCITY_SSH_REQUEST_TOKEN").append("=").append(sendEnv).append("\"");
        }
        final String sshCommandOptions = myCtx.getSshCommandOptions();
        if (StringUtil.isNotEmpty(sshCommandOptions)) {
          gitSshCommand.append(" ").append(sshCommandOptions);
        }
        if (myCtx.isDebugSsh() || settings.isTrace()) {
          gitSshCommand.append(" -vvv");
        }
        addEnvParam("GIT_SSH_COMMAND", gitSshCommand.toString());
      }
    } catch (Exception e) {
      if (privateKey != null)
        FileUtil.delete(privateKey);
      if (e instanceof VcsException)
        throw (VcsException) e;
      throw new VcsException(e);
    }
  }

  @Nullable
  private String getSshRequestToken() {
    return myCtx.getSshRequestToken();
  }

  private boolean isIgnoreKnownHosts(@NotNull AuthSettings authSettings) {
    // see TW-74389
    final AuthenticationMethod authMethod = authSettings.getAuthMethod();
    return myCtx.sshIgnoreKnownHosts() && (authSettings.isIgnoreKnownHosts() ||
           authMethod == AuthenticationMethod.TEAMCITY_SSH_KEY ||
           authMethod == AuthenticationMethod.PRIVATE_KEY_FILE);
  }

  @Nullable
  private File getPrivateKey(@NotNull AuthSettings authSettings) throws VcsException {
    File privateKey = null;
    final boolean useSshAskPass = myCtx.isUseSshAskPass();
    try {
      switch (authSettings.getAuthMethod()) {
        case TEAMCITY_SSH_KEY:
          privateKey = getUploadedPrivateKey(authSettings);
          break;
        case PRIVATE_KEY_FILE:
          final String keyPath = authSettings.getPrivateKeyFilePath();
          if (StringUtil.isEmpty(keyPath)) {
            throw new VcsException("Authentication method is \"" + AuthenticationMethod.PRIVATE_KEY_FILE.uiName() + "\", but no private key path provided");
          }

          final File finalPrivateKey = createTmpKeyFile();
          addPostAction(() -> FileUtil.delete(finalPrivateKey));
          privateKey = finalPrivateKey;

          writeSshPrivateKeyToFile(Files.readAllBytes(Paths.get(keyPath)), privateKey);
          break;
        case PRIVATE_KEY_DEFAULT:
          // we do not decrypt default ssh keys
          return null;
        default:
          return null;
      }

      final String passphrase = authSettings.getPassphrase();
      if (useSshAskPass) {
        withAskPassScript(passphrase, askPassPath -> {
          addEnvParam("SSH_ASKPASS", askPassPath);
          addEnvParam("SSH_ASKPASS_REQUIRE", "force");
          addEnvParam("DISPLAY", ":0.0");
        });
      } else {
        final KeyPair keyPair = KeyPair.load(new JSch(), privateKey.getAbsolutePath());
        OutputStream out = null;
        try {
          out = new BufferedOutputStream(new FileOutputStream(privateKey));
          if (keyPair.isEncrypted() && !keyPair.decrypt(passphrase)) {
            throw new VcsException("Wrong SSH key passphrase");
          }
          keyPair.writePrivateKey(out, null);
        } finally {
          FileUtil.close(out);
        }
      }

      //set permissions to 600, without that ssh client rejects the key on *nix
      privateKey.setReadable(false, false);
      privateKey.setReadable(true, true);
      privateKey.setWritable(false, false);
      privateKey.setWritable(true, true);

      return privateKey;
    } catch (Exception e) {
      if (privateKey != null)
        FileUtil.delete(privateKey);
      if (e instanceof VcsException)
        throw (VcsException) e;
      throw new VcsException(e);
    }
  }

  private void writeSshPrivateKeyToFile(@NotNull byte[] privateKey, @NotNull File file) throws IOException {
    FileUtil.writeFileAndReportErrors(file, new String(privateKey).trim().replace("\r\n", "\n") + "\n");
  }

  private void withAskPassScript(@Nullable String pass, @NotNull Consumer<String> action) throws VcsException {
    File askPass = null;
    String askPassPath;
    try {
      askPass = myScriptGen.generateAskPass(pass);
      askPassPath = askPass.getAbsolutePath();
      if (askPassPath.contains(" ") && SystemInfo.isWindows) {
        askPassPath = GitUtils.getShortFileName(askPass);
      }
      action.accept(askPassPath);
    } catch (Exception e) {
      if (askPass != null) {
        FileUtil.delete(askPass);
      }
      throw new VcsException(e);
    }
    final File finalAskPass = askPass;
    addPostAction(() -> {
      if (myCtx.isDeleteTempFiles())
        FileUtil.delete(finalAskPass);
    });
  }

  @NotNull
  private File getUploadedPrivateKey(@NotNull AuthSettings authSettings) throws Exception {
    final String keyId = authSettings.getTeamCitySshKeyId();
    final VcsRoot root = authSettings.getRoot();
    if (keyId == null ||  root == null || mySshKeyManager == null) {
      final String msg = "Failed to locate uploaded SSH key " + keyId + " for vcs root %s " + (mySshKeyManager == null ? ": null ssh key manager" : "");
      Loggers.VCS.warn(String.format(msg,  LogUtil.describe(root)));
      throw new VcsException(String.format(msg, root == null ? null : root.getName()));
    }

    final TeamCitySshKey key = mySshKeyManager.getKey(root);
    if (key == null) {
      throw new VcsException("Failed to locate uploaded SSH key " + keyId + " for vcs root " + root.getName());
    }
    final File privateKey = createTmpKeyFile();
    addPostAction(() -> FileUtil.delete(privateKey));

    writeSshPrivateKeyToFile(key.getPrivateKey(), privateKey);
    return privateKey;
  }

  private File createTmpKeyFile() throws IOException {
    return FileUtil.createTempFile(myCtx.getTempDir(), "key", "", true);
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
        throw new SshKeyNotFoundException("Failed to retrieve uploaded ssh key for root " + LogUtil.describe(root));
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

  public boolean isAbnormalExitExpected() {
    return myAbnormalExitExpected;
  }

  @NotNull
  public GitCommandLine abnormalExitExpected(boolean abnormalExitExpected) {
    myAbnormalExitExpected = abnormalExitExpected;
    return this;
  }

  public String getStdErrLogLevel() {
    return myStdErrLogLevel;
  }

  @NotNull
  public GitCommandLine stdErrLogLevel(@NotNull String stdErrLogLevel) {
    myStdErrLogLevel = stdErrLogLevel;
    return this;
  }

  public boolean isStdErrExpected() {
    return myStdErrExpected;
  }

  @NotNull
  public GitCommandLine stdErrExpected(boolean stdErrExpected) {
    myStdErrExpected = stdErrExpected;
    return this;
  }

  @NotNull
  public Context getContext() {
    return myCtx;
  }
}