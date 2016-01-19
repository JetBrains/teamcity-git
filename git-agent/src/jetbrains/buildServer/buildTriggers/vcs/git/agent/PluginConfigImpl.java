/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements AgentPluginConfig {

  private final static Logger LOG = Logger.getLogger(PluginConfigImpl.class);

  public static final String IDLE_TIMEOUT = "teamcity.git.idle.timeout.seconds";
  public static final String USE_NATIVE_SSH = "teamcity.git.use.native.ssh";
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


  private int parseTimeout(String valueFromBuild) {
    return parseTimeout(valueFromBuild, DEFAULT_IDLE_TIMEOUT);
  }

  private int parseTimeout(String valueFromBuild, int defaultValue) {
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
