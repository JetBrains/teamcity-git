

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildInterruptReason;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildContext implements Context {

  private final AgentRunningBuild myBuild;
  private final AgentPluginConfig myConfig;
  private final GitProgressLogger myLogger;

  public BuildContext(@NotNull AgentRunningBuild build,
                      @NotNull AgentPluginConfig config) {
    myBuild = build;
    myConfig = config;
    myLogger = new GitBuildProgressLogger(build.getBuildLogger().getFlowLogger("-1"), config.getGitProgressMode());
  }


  @Nullable
  public String getInterruptionReason() {
    final BuildInterruptReason reason = myBuild.getInterruptReason();
    return reason == null ? null : reason.getUserDescription();
  }

  @Nullable
  @Override
  public String getSshMacType() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.sshMacType");
    if (!StringUtil.isEmpty(value))
      return value;
    return null;
  }

  @Nullable
  @Override
  public String getPreferredSshAuthMethods() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.sshPreferredAuthMethods");
    if (!StringUtil.isEmpty(value))
      return value;
    return "publickey,keyboard-interactive,password";
  }

  @Override
  public boolean isProvideCredHelper() {
    return myConfig.isProvideCredHelper() && !getGitVersion().isLessThan(UpdaterImpl.EMPTY_CRED_HELPER);
  }

  @Override
  public boolean isCleanCredHelperScript() {
    return myConfig.isCleanCredHelperScript();
  }

  @Override
  public boolean isDebugSsh() {
    return myConfig.isDebugSsh();
  }

  @Nullable
  @Override
  public Charset getCharset() {
    final String charsetName = myConfig.getGitOutputCharsetName();
    if (charsetName != null) {
      try {
        return Charset.forName(charsetName);
      } catch (UnsupportedCharsetException ignored) {
        // return below
      }
    }
    return null;
  }

  @Override
  public boolean isDeleteTempFiles() {
    return myConfig.isDeleteTempFiles();
  }

  @Override
  public boolean isUseGitSshCommand() {
    return myConfig.isUseGitSshCommand() && !getGitVersion().isLessThan(UpdaterImpl.MIN_GIT_SSH_COMMAND);
  }

  @NotNull
  @Override
  public File getTempDir() {
    return myBuild.getBuildTempDirectory();
  }

  @NotNull
  @Override
  public GitExec getGitExec() {
    return myConfig.getGitExec();
  }

  @NotNull
  @Override
  public GitVersion getGitVersion() {
    return getGitExec().getVersion();
  }

  @NotNull
  @Override
  public Map<String, String> getEnv() {
    return myConfig.isRunGitWithBuildEnv() ? myBuild.getBuildParameters().getEnvironmentVariables() : Collections.emptyMap();
  }

  @Override
  public int getIdleTimeoutSeconds() {
    return myConfig.getIdleTimeoutSeconds();
  }

  @Nullable
  @Override
  public String getSshRequestToken() {
    return myConfig.getSshRequestToken();
  }

  @NotNull
  @Override
  public Collection<String> getCustomConfig() {
    return myConfig.getCustomConfig();
  }

  @NotNull
  @Override
  public GitProgressLogger getLogger() {
    return myLogger;
  }

  @Override
  public boolean isDebugGitCommands() {
    return true;
  }

  @NotNull
  @Override
  public List<String> getKnownRepoLocations() {
    return Arrays.asList(myBuild.getAgentConfiguration().getWorkDirectory().getAbsolutePath(), myConfig.getCachesDir().getAbsolutePath());
  }

  @Override
  public boolean isUseSshAskPass() {
    final String p = myBuild.getSharedConfigParameters().get("teamcity.internal.git.useSshAskPass");
    return p == null || Boolean.parseBoolean(p);
  }

  @Nullable
  @Override
  public String getSshCommandOptions() {
    return myBuild.getSharedConfigParameters().get("teamcity.internal.git.sshCommandOptions");
  }
}