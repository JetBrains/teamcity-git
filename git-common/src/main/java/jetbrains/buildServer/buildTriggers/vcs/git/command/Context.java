

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Context {

  @Nullable
  String getInterruptionReason();

  @Nullable
  String getSshMacType();

  @Nullable
  String getPreferredSshAuthMethods();

  boolean isProvideCredHelper();

  boolean isCleanCredHelperScript();

  boolean sshIgnoreKnownHosts();

  @Nullable
  String getSshKnownHosts();

  @Nullable
  Charset getCharset();

  boolean isDebugSsh();

  boolean isDeleteTempFiles();

  boolean isUseGitSshCommand();

  @NotNull
  File getTempDir();

  @NotNull
  Map<String, String> getEnv();

  @NotNull
  GitExec getGitExec();

  @NotNull
  GitVersion getGitVersion();

  int getIdleTimeoutSeconds();

  @Nullable
  String getSshRequestToken();

  @NotNull
  Collection<String> getCustomConfig();

  @NotNull
  GitProgressLogger getLogger();

  boolean isDebugGitCommands();

  @NotNull
  List<String> getKnownRepoLocations();

  boolean isUseSshAskPass();

  @Nullable
  String getSshCommandOptions();

  int getSshConnectTimeoutSeconds();

  @NotNull
  Collection<String> getCustomRecoverableMessages();

  /**
   * @return internal property or null if it is absent.
   * On agent tries to get a property from the agent properties. If it's absent, then tries to get it from build parameters
   */
  @Nullable
  String getInternalProperty(@NotNull String key);

  default boolean getBooleanInternalProperty(@NotNull String key, boolean defaultValue) {
    String value = getInternalProperty(key);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
  }
}