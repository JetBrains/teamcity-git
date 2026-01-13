

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import jetbrains.buildServer.DevelopmentMode;
import jetbrains.buildServer.agent.AgentMiscConstants;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.AgentRuntimeProperties;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements AgentPluginConfig {

  public static final String GIT_TRACE_ENV = "teamcity.internal.git.traceEnv";

  public static final String IDLE_TIMEOUT = "teamcity.git.idle.timeout.seconds";
  public static final String USE_NATIVE_SSH = "teamcity.git.use.native.ssh";
  public static final String USE_GIT_SSH_COMMAND = "teamcity.git.useGitSshCommand";
  public static final String USE_MIRRORS = "teamcity.git.use.local.mirrors";
  public static final String USE_MIRRORS_FOR_SUBMODULES = "teamcity.internal.git.agent.submodules.useMirrors";
  public static final String USE_ALTERNATES = "teamcity.git.useAlternates";
  public static final String USE_SHALLOW_CLONE = "teamcity.git.shallowClone";
  /** @deprecated preserved for backward compatibility, see TW-71077 */
  public static final String USE_SHALLOW_CLONE_FROM_MIRROR_TO_CHECKOUT_DIR = "teamcity.git.use.shallow.clone";
  public static final String USE_SHALLOW_CLONE_INTERNAL = "teamcity.internal.git.agent.shallowClone";
  public static final String TEAMCITY_DONT_DELETE_TEMP_FILES = "teamcity.dont.delete.temp.files";
  public static final String USE_MAIN_REPO_USER_FOR_SUBMODULES = "teamcity.git.useMainRepoUserForSubmodules";
  public static final String VCS_ROOT_MIRRORS_STRATEGY = "teamcity.git.mirrorStrategy";
  public static final String VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES = "alternates";
  public static final String VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY = "mirrors";
  public static final String USE_SPARSE_CHECKOUT = "teamcity.git.useSparseCheckout";
  public static final String USE_BUILD_ENV = "teamcity.git.useBuildEnv";
  public static final String FETCH_ALL_HEADS = "teamcity.git.fetchAllHeads";
  public static final String FETCH_TAGS = "teamcity.git.fetchTags";
  public static final String EXCLUDE_USERNAME_FROM_HTTP_URL = "teamcity.git.excludeUsernameFromHttpUrl";
  public static final String CLEAN_CRED_HELPER_SCRIPT = "teamcity.git.cleanCredHelperScript";
  public static final String PROVIDE_CRED_HELPER = "teamcity.git.provideCredentialHelper";
  private static final String USE_DEFAULT_CHARSET = "teamcity.git.useDefaultCharset";
  private static final String GIT_OUTPUT_CHARSET = "teamcity.git.outputCharset";
  private static final String LS_REMOTE_TIMEOUT_SECONDS = "teamcity.git.lsRemoteTimeoutSeconds";
  private static final String SUBMODULE_UPDATE_TIMEOUT_SECONDS = "teamcity.internal.git.agent.submodules.update.timeout.seconds";
  public static final String SSH_SEND_ENV_REQUEST_TOKEN = "sshSendEnvRequestToken";
  public static final String SSH_CONNECT_TIMEOUT_SECONDS = "teamcity.git.ssh.connect.timeout.seconds";
  public static final String CLEAN_RESPECTS_OTHER_ROOTS = "teamcity.internal.git.cleanRespectsOtherRoots";
  public static final String CUSTOM_GIT_CONFIG = "teamcity.internal.git.customConfig";
  public static final String REMOTE_OPERATION_ATTEMPTS = "teamcity.internal.git.remoteOperationAttempts";
  public static final String TEAMCITY_GIT_SSH_DEBUG = "teamcity.internal.git.sshDebug";
  private static final String CUSTOM_RECOVERABLE_MESSAGES = "teamcity.git.agent.recoverableMessages";
  public static final String SHALLOW_CLONE_DEPTH = "teamcity.git.agent.shallowCloneDepth";
  public static final String SUBMODULES_SHALLOW_DEPTH = "teamcity.git.agent.submodules.shallowCloneDepth";

  public static final String IGNORE_CHECKOUT_RULES_POSIFIX_CHECK_PARAMETER = "teamcity.internal.git.agent.ignoreCheckoutRulesPostfixCheck";
  private final static Logger LOG = Logger.getInstance(PluginConfigImpl.class);

  private static final Pattern NEW_LINE = Pattern.compile("(\r\n|\r|\n)");

  private final BuildAgentConfiguration myAgentConfig;
  private final AgentRunningBuild myBuild;
  private final VcsRoot myVcsRoot;
  private final GitExec myGitExec;
  private final Collection<String> myCustomConfig;

  public PluginConfigImpl(@NotNull BuildAgentConfiguration agentConfig,
                          @NotNull AgentRunningBuild build,
                          @NotNull VcsRoot vcsRoot,
                          @NotNull GitExec gitExec) {
    myAgentConfig = agentConfig;
    myBuild = build;
    myVcsRoot = vcsRoot;
    myGitExec = gitExec;
    myCustomConfig = parseCustomConfig();
  }


  @NotNull
  public File getCachesDir() {
    return myAgentConfig.getCacheDirectory("git");
  }


  public int getIdleTimeoutSeconds() {
    String valueFromBuild = myBuild.getSharedConfigParameters().get(IDLE_TIMEOUT);
    if (valueFromBuild != null)
      return parseTimeout(valueFromBuild);
    else
      return DEFAULT_IDLE_TIMEOUT;
  }


  @NotNull
  public String getPathToGit() {
    return myGitExec.getPath();
  }


  public boolean isUseNativeSSH() {
    String value = myBuild.getSharedConfigParameters().get(USE_NATIVE_SSH);
    return "true".equals(value);
  }


  @Override
  public boolean isUseGitSshCommand() {
    String value = myBuild.getSharedConfigParameters().get(USE_GIT_SSH_COMMAND);
    return !"false".equals(value);
  }

  public boolean isUseLocalMirrors(@NotNull AgentCheckoutPolicy rootSetting) {
    final String buildSetting = myBuild.getSharedConfigParameters().get(USE_MIRRORS);
    if (StringUtil.isNotEmpty(buildSetting)) {
      final boolean enabled = Boolean.parseBoolean(buildSetting);
      myBuild.getBuildLogger().message("Mirrors without alternates " + (enabled ? "enabled" : "disabled") + " via " + USE_MIRRORS + " build configuration parameter");
      return enabled;
    }

    final String mirrorStrategy = getMirrorStrategy();
    if (AgentCheckoutPolicy.USE_MIRRORS == rootSetting && VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY.equals(mirrorStrategy)) {
      myBuild.getBuildLogger().message("Mirrors without alternates enabled via " + VCS_ROOT_MIRRORS_STRATEGY + " build configuration parameter");
      return true;
    }
    return false;
  }

  @Override
  public boolean isUseLocalMirrors(@NotNull GitVcsRoot root) {
    return isUseLocalMirrors(root.getAgentCheckoutPolicy());
  }

  @Override
  public boolean isUseLocalMirrorsForSubmodules(@NotNull final GitVcsRoot root) {
    String param = myBuild.getSharedConfigParameters().get(USE_MIRRORS_FOR_SUBMODULES);
    if (StringUtil.isEmpty(param)) {
      param = myBuild.getSharedConfigParameters().get("teamcity.git.useMirrorsForSubmodules");
    }
    return StringUtil.isEmpty(param) || Boolean.parseBoolean(param);
  }

  @Override
  public boolean isUseAlternates(@NotNull GitVcsRoot root) {
    final AgentCheckoutPolicy rootSetting = root.getAgentCheckoutPolicy();
    final String buildSetting = myBuild.getSharedConfigParameters().get(USE_ALTERNATES);
    if (StringUtil.isNotEmpty(buildSetting)) {
      final boolean enabled = Boolean.parseBoolean(buildSetting);
      myBuild.getBuildLogger().message("Mirrors with alternates " + (enabled ? "enabled" : "disabled") + " via " + USE_ALTERNATES + " build configuration parameter");
      return enabled;
    }

    if (AgentCheckoutPolicy.USE_MIRRORS == rootSetting || AgentCheckoutPolicy.AUTO == rootSetting && !isShortLivedAgentWithoutMirror(root.getRepositoryDir())) {
      final String mirrorStrategy = getMirrorStrategy();
      if (StringUtil.isEmpty(mirrorStrategy) || VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES.equals(mirrorStrategy)) {
        myBuild.getBuildLogger().debug(AgentCheckoutPolicy.USE_MIRRORS == rootSetting ? "Mirrors enabled via VCS root settings" : "Mirrors automatically enabled");
        return true;
      }
      myBuild.getBuildLogger().message("Mirrors with alternates disabled via " + VCS_ROOT_MIRRORS_STRATEGY + " build configuration parameter");
    }
    return false;
  }

  public boolean isUseSparseCheckout() {
    String buildSetting = myBuild.getSharedConfigParameters().get(USE_SPARSE_CHECKOUT);
    if (StringUtil.isEmpty(buildSetting))
      return true;
    return Boolean.parseBoolean(buildSetting);
  }


  public boolean isRunGitWithBuildEnv() {
    String buildSetting = myBuild.getSharedConfigParameters().get(USE_BUILD_ENV);
    if (StringUtil.isEmpty(buildSetting))
      return false;
    return Boolean.parseBoolean(buildSetting);
  }

  @Nullable
  private String getMirrorStrategy() {
    return myBuild.getSharedConfigParameters().get(VCS_ROOT_MIRRORS_STRATEGY);
  }

  public boolean isUseShallowClone(@NotNull GitVcsRoot root) {
    final AgentCheckoutPolicy rootSetting = root.getAgentCheckoutPolicy();
    final String buildSetting = myBuild.getSharedConfigParameters().get(USE_SHALLOW_CLONE_INTERNAL);
    if (StringUtil.isNotEmpty(buildSetting)) {
      final boolean enabled = Boolean.parseBoolean(buildSetting);
      myBuild.getBuildLogger().message("Shallow clone " + (enabled ? "enforced" : "disabled") + " via " + USE_SHALLOW_CLONE_INTERNAL + " build configuration parameter");
      return enabled;
    }
    final String agentSetting = myAgentConfig.getConfigurationParameters().get(USE_SHALLOW_CLONE);
    if (StringUtil.isNotEmpty(agentSetting)) {
      final boolean enabled = Boolean.parseBoolean(agentSetting);
      myBuild.getBuildLogger().message("Shallow clone " + (enabled ? "enforced" : "disabled") + " via " + USE_SHALLOW_CLONE + " agent configuration property");
      return enabled;
    }
    if (AgentCheckoutPolicy.SHALLOW_CLONE == rootSetting) {
      myBuild.getBuildLogger().debug("Shallow clone enforced via VCS root settings");
      return true;
    }
    // if there is a mirror available on a short-lived agent, there is no need to run shallow clone
    if (AgentCheckoutPolicy.AUTO == rootSetting && isShortLivedAgentWithoutMirror(root.getRepositoryDir())) {
      myBuild.getBuildLogger().message("Shallow clone automatically enabled on a short-lived agent");
      return true;
    }
    return false;
  }

  private boolean isAgentTerminatedAfterBuild() {
    return "true".equals(myAgentConfig.getConfigurationParameters().get(AgentMiscConstants.IS_EPHEMERAL_AGENT_PROP));
  }

  private boolean isShortLivedAgentWithoutMirror(@NotNull File mirror) {
    return isAgentTerminatedAfterBuild() && FileUtil.isEmptyDir(mirror);
  }

  @Override
  public boolean isUseShallowCloneFromMirrorToCheckoutDir() {
    final String valueFromBuildConfiguration = myBuild.getSharedConfigParameters().get(USE_SHALLOW_CLONE_FROM_MIRROR_TO_CHECKOUT_DIR);
    if (valueFromBuildConfiguration != null) {
      return "true".equals(valueFromBuildConfiguration);
    } else {
      return "true".equals(myAgentConfig.getConfigurationParameters().get(USE_SHALLOW_CLONE_FROM_MIRROR_TO_CHECKOUT_DIR));
    }
  }

  public boolean isDeleteTempFiles() {
    boolean doNotDelete = Boolean.parseBoolean(myBuild.getSharedConfigParameters().get(TEAMCITY_DONT_DELETE_TEMP_FILES));
    return !doNotDelete;
  }


  @NotNull
  @Override
  public FetchHeadsMode getFetchHeadsMode() {
    final String fetchAllHeads = getFetchAllHeadsModeStr();
    if (StringUtil.isEmpty(fetchAllHeads) || "false".equals(fetchAllHeads) || "afterBuildBranch".equals(fetchAllHeads))
      return FetchHeadsMode.AFTER_BUILD_BRANCH;

    if ("true".equals(fetchAllHeads) || "always".equals(fetchAllHeads))
      return FetchHeadsMode.ALWAYS;

    if ("beforeBuildBranch".equals(fetchAllHeads))
      return FetchHeadsMode.BEFORE_BUILD_BRANCH;

    LOG.warn("Unsupported value of the " + FETCH_ALL_HEADS + " parameter: '" + fetchAllHeads + "', treat it as false");
    return FetchHeadsMode.AFTER_BUILD_BRANCH;
  }

  @Nullable
  @Override
  public String getFetchAllHeadsModeStr() {
    return myBuild.getSharedConfigParameters().get(FETCH_ALL_HEADS);
  }

  public boolean isUseMainRepoUserForSubmodules() {
    String fromBuildConfiguration = myBuild.getSharedConfigParameters().get(USE_MAIN_REPO_USER_FOR_SUBMODULES);
    if (fromBuildConfiguration != null)
      return Boolean.parseBoolean(fromBuildConfiguration);

    String fromAgentConfig = myAgentConfig.getConfigurationParameters().get(USE_MAIN_REPO_USER_FOR_SUBMODULES);
    if (fromAgentConfig != null)
      return Boolean.parseBoolean(fromAgentConfig);

    return true;
  }

  @NotNull
  public GitVersion getGitVersion() {
    return myGitExec.getVersion();
  }

  @NotNull
  public GitExec getGitExec() {
    return myGitExec;
  }

  public int getCheckoutIdleTimeoutSeconds() {
    String valueFromBuild = myBuild.getSharedConfigParameters().get(IDLE_TIMEOUT);
    if (valueFromBuild != null) {
      return parseTimeout(valueFromBuild, CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC);
    } else {
      return CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
    }
  }

  public boolean isUpdateSubmoduleOriginUrl() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.updateSubmoduleOriginUrl");
    return !"false".equals(value);
  }

  @Override
  public boolean isFailOnCleanCheckout() {
    return "true".equals(myBuild.getSharedConfigParameters().get(AgentRuntimeProperties.FAIL_ON_CLEAN_CHECKOUT));
  }

  @Override
  public int maxRepositorySizeForFsckGiB() {
    return Integer.parseInt(myBuild.getSharedConfigParameters().getOrDefault("teamcity.git.maxRepoSizeForFsckGiB", "5"));
  }

  @Override
  public boolean isFetchTags() {
    String value = myBuild.getSharedConfigParameters().get(FETCH_TAGS);
    //by default tags are fetched
    return !"false".equals(value);
  }

  public boolean isCredHelperMatchesAllUrls() {
    //it looks to be safe to enable all urls matching by default because we did
    //a similar thing with ask-pass script: it provides password for any server
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.credentialHelperMatchesAllUrls");
    return !"false".equals(value);
  }

  @NotNull
  @Override
  public GitProgressMode getGitProgressMode() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.progressMode");
    if (value == null)
      return GitProgressMode.DEBUG;
    try {
      return GitProgressMode.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return GitProgressMode.DEBUG;
    }
  }

  @Override
  public boolean isExcludeUsernameFromHttpUrl() {
    String value = myBuild.getSharedConfigParameters().get(EXCLUDE_USERNAME_FROM_HTTP_URL);
    return !"false".equals(value);
  }

  @Override
  public boolean isCleanCredHelperScript() {
    String value = myBuild.getSharedConfigParameters().get(CLEAN_CRED_HELPER_SCRIPT);
    return !"false".equals(value);
  }

  @Override
  public boolean isProvideCredHelper() {
    String value = myBuild.getSharedConfigParameters().get(PROVIDE_CRED_HELPER);
    return !"false".equals(value);
  }

  @Nullable
  @Override
  public String getGitOutputCharsetName() {
    String useDefault = myBuild.getSharedConfigParameters().get(USE_DEFAULT_CHARSET);
    if (Boolean.valueOf(useDefault))
      return null;
    String charsetName = myBuild.getSharedConfigParameters().get(GIT_OUTPUT_CHARSET);
    return StringUtil.isNotEmpty(charsetName) ? charsetName : "UTF-8";
  }

  @Override
  public int getLsRemoteTimeoutSeconds() {
    int defaultTimeoutSeconds = 5 * 60;
    String valueFromBuild = myBuild.getSharedConfigParameters().get(LS_REMOTE_TIMEOUT_SECONDS);
    if (valueFromBuild != null) {
      return parseTimeout(valueFromBuild, defaultTimeoutSeconds);
    } else {
      return defaultTimeoutSeconds;
    }
  }

  @Override
  public int getSubmoduleUpdateTimeoutSeconds() {
    return parseTimeout(myBuild.getSharedConfigParameters().get(SUBMODULE_UPDATE_TIMEOUT_SECONDS), CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC);
  }

  @Nullable
  @Override
  public String getSshRequestToken() {
    return myBuild.getSharedConfigParameters().get("vcsroot." + myVcsRoot.getExternalId() + "." + SSH_SEND_ENV_REQUEST_TOKEN);
  }

  @Override
  public boolean isCleanCommandRespectsOtherRoots() {
    final String p = myBuild.getSharedConfigParameters().get(CLEAN_RESPECTS_OTHER_ROOTS);
    return p == null || Boolean.parseBoolean(p);
  }

  @NotNull
  @Override
  public Collection<String> getCustomConfig() {
    return myCustomConfig;
  }

  @Override
  public int getRemoteOperationAttempts() {
    final String param = myBuild.getSharedConfigParameters().get(REMOTE_OPERATION_ATTEMPTS);
    if (StringUtil.isNotEmpty(param)) {
      try {
        return Integer.parseInt(param);
      } catch (NumberFormatException e) {
        // return below
      }
    }
    return 3;
  }

  @Override
  public boolean isDebugSsh() {
    return Loggers.VCS.isDebugEnabled() || Boolean.parseBoolean(myBuild.getSharedConfigParameters().get(TEAMCITY_GIT_SSH_DEBUG));
  }

  @Override
  public boolean isNoFetchRequiredIfRevisionInRepo() {
    return Boolean.parseBoolean(myBuild.getSharedConfigParameters().get("teamcity.git.noFetchIfRevisionInRepo"));
  }

  @Override
  public boolean isNoShowForcedUpdates() {
    String buildParameterNoShowForcedUpdates = myBuild.getSharedConfigParameters().get("teamcity.git.noShowForcedUpdates");
    if (buildParameterNoShowForcedUpdates == null) {
      return true;
    }

    return Boolean.parseBoolean(buildParameterNoShowForcedUpdates);
  }


  @NotNull
  private Collection<String> parseCustomConfig() {
    String customConfig = myBuild.getSharedConfigParameters().get(CUSTOM_GIT_CONFIG);
    if (customConfig == null) {
      customConfig = myVcsRoot.getProperty(Constants.CUSTOM_GIT_CONFIG);
    }
    return StringUtil.isEmptyOrSpaces(customConfig) ? Collections.emptyList() : Arrays.asList(NEW_LINE.split(customConfig));
  }

  private int parseTimeout(String valueFromBuild) {
    return parseTimeout(valueFromBuild, DEFAULT_IDLE_TIMEOUT);
  }

  private int parseTimeout(String valueFromBuild, int defaultValue) {
    if (valueFromBuild == null)
      return defaultValue;
    try {
      int timeout = Integer.parseInt(valueFromBuild);
      if (timeout > 0)
        return timeout;
      else
        return defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @NotNull
  @Override
  public Map<String, String> getGitTraceEnv() {
    final String prop = myBuild.getSharedConfigParameters().get(GIT_TRACE_ENV);
    if (StringUtil.isEmpty(prop)) return Collections.emptyMap();
    try {
      return PropertiesUtil.toMap(PropertiesUtil.loadProperties(new ByteArrayInputStream(prop.replace(' ', '\n').getBytes(StandardCharsets.UTF_8))));
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to parse \"" + GIT_TRACE_ENV + "\" property value \"" + prop + "\", git trace won't be enabled", e);
      return Collections.emptyMap();
    }
  }

  @NotNull
  @Override
  public Collection<String> getCustomRecoverableMessages() {
    final String property = myBuild.getSharedConfigParameters().get(CUSTOM_RECOVERABLE_MESSAGES);
    if (StringUtil.isEmptyOrSpaces(property)) return Collections.emptyList();

    return StringUtil.split(property, true, ';');
  }

  @Override
  public boolean shouldIgnoreCheckoutRulesPostfixCheck() {
    return Boolean.parseBoolean(myBuild.getSharedConfigParameters().get(IGNORE_CHECKOUT_RULES_POSIFIX_CHECK_PARAMETER));
  }

  @Override
  public int getSshConnectTimeoutSeconds() {
    final String valueFromBuild = myBuild.getSharedConfigParameters().get(SSH_CONNECT_TIMEOUT_SECONDS);
    if (valueFromBuild == null) {
      return DEFAULT_SSH_CONNECT_TIMEOUT;
    }

    return parseTimeout(valueFromBuild, DEFAULT_SSH_CONNECT_TIMEOUT);
  }

  @Override
  public boolean isAllowFileUrl() {
    return DevelopmentMode.isEnabled || TeamCityProperties.getBooleanOrTrue(Constants.ALLOW_FILE_URL);
  }

  @Override
  public int getShallowCloneDepth() {
    return getDepthParameter(SHALLOW_CLONE_DEPTH, 1);
  }

  @Override
  public int getSubmodulesShallowDepth() {
    return getDepthParameter(SUBMODULES_SHALLOW_DEPTH, 1);
  }

  @SuppressWarnings("SameParameterValue")
  private int getDepthParameter(@NotNull String parameterName, int defaultValue) {
    String depth = myBuild.getSharedConfigParameters().get(parameterName);
    if (depth != null) {
      try {
        int parsedDepth = Integer.parseInt(depth);
        if (parsedDepth < 1) {
          LOG.warn("Invalid value for " + parameterName + ": '" + depth + "' (must be >= 1), using default depth of " + defaultValue);
          return defaultValue;
        }
        return parsedDepth;
      } catch (NumberFormatException e) {
        LOG.warn("Invalid value for " + parameterName + ": '" + depth + "', using default depth of " + defaultValue);
      }
    }
    return defaultValue;
  }
}