package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.ServerSshKnownHostsContext;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcshostings.url.ServerURI;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextImpl implements Context {

  private final ServerPluginConfig myConfig;
  private final GitProgress myProgress;
  private final GitExec myGitExec;
  private final GitVcsRoot myRoot;
  private final SshKnownHostsManager myKnownHostsManager;

  public ContextImpl(@Nullable GitVcsRoot root, @NotNull ServerPluginConfig config, @NotNull GitExec gitExec, @NotNull SshKnownHostsManager knownHostsManager) {
    this(root, config, gitExec, GitProgress.NO_OP, knownHostsManager);
  }

  public ContextImpl(@Nullable GitVcsRoot root, @NotNull ServerPluginConfig config, @NotNull GitExec gitExec, @NotNull GitProgress progress, @NotNull SshKnownHostsManager knownHostsManager) {
    myRoot = root;
    myConfig = config;
    myGitExec = gitExec;
    myProgress = progress;
    myKnownHostsManager = knownHostsManager;
  }

  @Nullable
  @Override
  public String getInterruptionReason() {
    return null;
  }

  @Nullable
  @Override
  public String getSshMacType() {
    return null;
  }

  @Nullable
  @Override
  public String getPreferredSshAuthMethods() {
    return "publickey,keyboard-interactive,password";
  }

  @Override
  public boolean isProvideCredHelper() {
    return true;
  }

  @Override
  public boolean isCleanCredHelperScript() {
    return true;
  }

  @Override
  public boolean knownHostsEnabled() {
    return myKnownHostsManager.isKnownHostsEnabled(ServerSshKnownHostsContext.INSTANCE);
  }

  @Nullable
  @Override
  public String getSshKnownHosts(@Nullable AuthSettings settings) {
    if (!myKnownHostsManager.isKnownHostsEnabled(ServerSshKnownHostsContext.INSTANCE)) {
      return null;
    }
    if (settings != null) {
      VcsRoot root = Objects.requireNonNull(settings.getRoot());
      String fetchUrl = Objects.requireNonNull(root.getProperty(Constants.FETCH_URL));
      try {
        ServerURI serverURI = ServerURIParser.createServerURI(fetchUrl);
        String portString = serverURI.getPort();
        int port = portString != null ? Integer.parseInt(portString) : 0;
        myKnownHostsManager.updateKnownHosts(ServerSshKnownHostsContext.INSTANCE, serverURI.getHost(), port);
      } catch (Exception ignored) {
      }
    }
    return myKnownHostsManager.getKnownHosts(ServerSshKnownHostsContext.INSTANCE);
  }

  @Nullable
  @Override
  public Charset getCharset() {
    final String charsetName = myConfig.getGitOutputCharsetName();
    if (charsetName == null) return null;
    try {
      return Charset.forName(charsetName);
    } catch (UnsupportedCharsetException e) {
      return Charset.forName("UTF-8");
    }
  }

  @Override
  public boolean isDebugSsh() {
    return isDebugGitCommands();
  }

  @Override
  public boolean isDeleteTempFiles() {
    return true;
  }

  @Override
  public boolean isUseGitSshCommand() {
    return true;
  }

  @NotNull
  @Override
  public File getTempDir() {
    return new File(FileUtil.getTempDirectory());
  }

  @NotNull
  @Override
  public Map<String, String> getEnv() {
    return myConfig.passEnvToChildProcess() ? System.getenv() : Collections.emptyMap();
  }

  @NotNull
  @Override
  public GitExec getGitExec() {
    return myGitExec;
  }

  @NotNull
  @Override
  public GitVersion getGitVersion() {
    return getGitExec().getVersion();
  }

  @Override
  public int getIdleTimeoutSeconds() {
    return myConfig.getIdleTimeoutSeconds();
  }

  @Nullable
  @Override
  public String getSshRequestToken() {
    if (myRoot == null) return null;
    if (TeamCityProperties.getBooleanOrTrue("teamcity.git.sendSshSendEnvRequestToken")) {
      final String token = myRoot.getProperty("sshSendEnvRequestToken");
      if (token == null) return null;
      return token.contains("%") ? null : token;
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<String> getCustomConfig() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public GitProgressLogger getLogger() {
    return new GitProgressLogger() {
      @Override
      public void openBlock(@NotNull String name) {
        message(name);
      }

      @Override
      public void message(@NotNull String message) {
        myProgress.reportProgress(message);
      }

      @Override
      public void warning(@NotNull String message) {
        message(message);
      }

      @Override
      public void progressMessage(@NotNull String message) {
        message(message);
      }

      @Override
      public void closeBlock(@NotNull String name) {

      }
    };
  }

  @Override
  public boolean isDebugGitCommands() {
    return TeamCityProperties.getBoolean("teamcity.git.debugNativeGit");
  }

  @NotNull
  @Override
  public List<String> getKnownRepoLocations() {
    return Collections.singletonList(myConfig.getCachesDir().getAbsolutePath());
  }

  @Override
  public boolean isUseSshAskPass() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.useSshAskPass");
  }

  @Nullable
  @Override
  public String getSshCommandOptions() {
    return TeamCityProperties.getPropertyOrNull("teamcity.git.sshCommandOptions");
  }

  @Override
  public int getSshConnectTimeoutSeconds() {
    return myConfig.getSshConnectTimeoutSeconds();
  }

  @NotNull
  @Override
  public Collection<String> getCustomRecoverableMessages() {
    return myConfig.getCustomRecoverableMessages();
  }

  @Nullable
  @Override
  public String getInternalProperty(@NotNull String key) {
    return TeamCityProperties.getPropertyOrNull(key);
  }
}
