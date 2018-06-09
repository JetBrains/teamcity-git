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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.AgentRuntimeProperties;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.CommandUtil;
import jetbrains.buildServer.util.StringUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements AgentPluginConfig {

  private final static Logger LOG = Logger.getLogger(PluginConfigImpl.class);

  public static final String IDLE_TIMEOUT = "teamcity.git.idle.timeout.seconds";
  public static final String USE_NATIVE_SSH = "teamcity.git.use.native.ssh";
  public static final String USE_GIT_SSH_COMMAND = "teamcity.git.useGitSshCommand";
  public static final String USE_MIRRORS = "teamcity.git.use.local.mirrors";
  public static final String USE_ALTERNATES = "teamcity.git.useAlternates";
  public static final String USE_SHALLOW_CLONE = "teamcity.git.use.shallow.clone";
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

  private final BuildAgentConfiguration myAgentConfig;
  private final AgentRunningBuild myBuild;
  private final GitExec myGitExec;

  public PluginConfigImpl(@NotNull BuildAgentConfiguration agentConfig,
                          @NotNull AgentRunningBuild build,
                          @NotNull GitExec gitExec) {
    myAgentConfig = agentConfig;
    myBuild = build;
    myGitExec = gitExec;
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

  public boolean isUseLocalMirrors(@NotNull GitVcsRoot root) {
    String buildSetting = myBuild.getSharedConfigParameters().get(USE_MIRRORS);
    if (!StringUtil.isEmpty(buildSetting)) {
      LOG.info("Use the '" + USE_MIRRORS + "' option specified in the build");
      return Boolean.parseBoolean(buildSetting);
    }

    Boolean rootSetting = root.isUseAgentMirrors();
    String mirrorStrategy = getMirrorStrategy();
    if (rootSetting != null && rootSetting && VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY.equals(mirrorStrategy)) {
      LOG.info("Use the mirrors option specified in the VCS root");
      return true;
    }

    return false;
  }


  public boolean isUseAlternates(@NotNull GitVcsRoot root) {
    String buildSetting = myBuild.getSharedConfigParameters().get(USE_ALTERNATES);
    if (!StringUtil.isEmpty(buildSetting)) {
      LOG.info("Use the '" + USE_ALTERNATES + "' option specified in the build");
      return Boolean.parseBoolean(buildSetting);
    }

    Boolean rootSetting = root.isUseAgentMirrors();
    String mirrorStrategy = getMirrorStrategy();
    if (rootSetting != null && rootSetting && VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES.equals(mirrorStrategy)) {
      LOG.info("Use the mirrors option specified in the VCS root");
      return true;
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

  @NotNull
  private String getMirrorStrategy() {
    String strategy = myBuild.getSharedConfigParameters().get(VCS_ROOT_MIRRORS_STRATEGY);
    if (!StringUtil.isEmpty(strategy))
      return strategy;
    return VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES;
  }

  public boolean isUseShallowClone() {
    String valueFromBuildConfiguration = myBuild.getSharedConfigParameters().get(USE_SHALLOW_CLONE);
    if (valueFromBuildConfiguration != null) {
      return "true".equals(valueFromBuildConfiguration);
    } else {
      String valueFromAgentConfig = myAgentConfig.getConfigurationParameters().get(USE_SHALLOW_CLONE);
      return "true".equals(valueFromAgentConfig);
    }
  }


  public boolean isDeleteTempFiles() {
    boolean doNotDelete = Boolean.parseBoolean(myBuild.getSharedConfigParameters().get(TEAMCITY_DONT_DELETE_TEMP_FILES));
    return !doNotDelete;
  }


  @NotNull
  @Override
  public FetchHeadsMode getFetchHeadsMode() {
    Map<String, String> params = myBuild.getSharedConfigParameters();
    String fetchAllHeads = params.get(FETCH_ALL_HEADS);
    if (StringUtil.isEmpty(fetchAllHeads) || "false".equals(fetchAllHeads) || "afterBuildBranch".equals(fetchAllHeads))
      return FetchHeadsMode.AFTER_BUILD_BRANCH;

    if ("true".equals(fetchAllHeads) || "always".equals(fetchAllHeads))
      return FetchHeadsMode.ALWAYS;

    if ("beforeBuildBranch".equals(fetchAllHeads))
      return FetchHeadsMode.BEFORE_BUILD_BRANCH;

    LOG.warn("Unsupported value of the " + FETCH_ALL_HEADS + " parameter: '" + fetchAllHeads + "', treat it as false");
    return FetchHeadsMode.AFTER_BUILD_BRANCH;
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
}
