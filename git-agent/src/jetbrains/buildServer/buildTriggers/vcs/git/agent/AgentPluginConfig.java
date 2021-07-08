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

import java.util.Collection;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public interface AgentPluginConfig extends PluginConfig {

  boolean isUseNativeSSH();

  boolean isUseGitSshCommand();

  boolean isUseLocalMirrors(@NotNull GitVcsRoot root);

  boolean isUseLocalMirrorsForSubmodules(@NotNull GitVcsRoot root);

  boolean isUseAlternates(@NotNull GitVcsRoot root);

  boolean isUseShallowClone(@NotNull GitVcsRoot root);

  /** @deprecated we preserve it for backward compatibility with "teamcity.git.use.shallow.clone" param in case of non-default configuration */
  boolean isUseShallowCloneFromMirrorToCheckoutDir();

  boolean isDeleteTempFiles();

  @NotNull
  FetchHeadsMode getFetchHeadsMode();

  @Nullable
  String getFetchAllHeadsModeStr();

  boolean isUseMainRepoUserForSubmodules();

  @NotNull
  GitVersion getGitVersion();

  @NotNull
  public GitExec getGitExec();

  int getCheckoutIdleTimeoutSeconds();

  boolean isUpdateSubmoduleOriginUrl();

  boolean isUseSparseCheckout();

  boolean isRunGitWithBuildEnv();

  boolean isFailOnCleanCheckout();

  boolean isFetchTags();

  boolean isCredHelperMatchesAllUrls();

  @NotNull
  GitProgressMode getGitProgressMode();

  boolean isExcludeUsernameFromHttpUrl();

  boolean isCleanCredHelperScript();

  boolean isProvideCredHelper();

  /**
   * Returns charset name for git output or null if the default charset should be used
   */
  @Nullable
  String getGitOutputCharsetName();

  int getLsRemoteTimeoutSeconds();

  int getSubmoduleUpdateTimeoutSeconds();

  @Nullable
  String getSshRequestToken();

  boolean isCleanCommandRespectsOtherRoots();

  @NotNull
  Collection<String> getCustomConfig();

  int getRemoteOperationAttempts();

  boolean isDebugSsh();

  boolean isNoFetchRequiredIfRevisionInRepo();

  /**
   * Defines how progress output from git commands is written into build log
   */
  enum GitProgressMode {
    /**
     * Don't write progress into build log
     */
    NONE,
    /**
     * Write progress as verbose messages
     */
    DEBUG,
    /**
     * Write progress as normal messages
     */
    NORMAL
  }
}
