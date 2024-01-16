

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubContext implements Context {

  private final GitExec myGitExec;
  private GitProgressLogger myLogger = GitProgressLogger.NO_OP;

  public StubContext() {
    this("git");
  }

  public StubContext(@NotNull String gitPath) {
    this(gitPath, GitVersion.MIN);
  }

  public StubContext(@NotNull String gitPath, @NotNull GitVersion version) {
    myGitExec = new GitExec(gitPath, version, null);
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
    return null;
  }

  @Override
  public boolean isProvideCredHelper() {
    return false;
  }

  @Override
  public boolean isCleanCredHelperScript() {
    return false;
  }

  @Override
  public boolean isDebugSsh() {
    return Loggers.VCS.isDebugEnabled();
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return null;
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
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public GitExec getGitExec() {
    return myGitExec;
  }

  @NotNull
  @Override
  public GitVersion getGitVersion() {
    return myGitExec.getVersion();
  }

  @Override
  public int getIdleTimeoutSeconds() {
    return PluginConfig.DEFAULT_IDLE_TIMEOUT;
  }

  @Nullable
  @Override
  public String getSshRequestToken() {
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
    return myLogger;
  }

  @Override
  public boolean isDebugGitCommands() {
    return false;
  }

  @NotNull
  @Override
  public List<String> getKnownRepoLocations() {
    return Collections.emptyList();
  }

  @Override
  public boolean isUseSshAskPass() {
    return false;
  }

  public void setLogger(@NotNull GitProgressLogger logger) {
    myLogger = logger;
  }

  @Nullable
  @Override
  public String getSshCommandOptions() {
    return null;
  }
}