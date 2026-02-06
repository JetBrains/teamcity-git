

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.util.Collection;
import java.util.Map;
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

  int maxRepositorySizeForFsckGiB();

  boolean isFetchTags();

  boolean isCredHelperMatchesAllUrls();

  @NotNull
  GitProgressMode getGitProgressMode();

  boolean isExcludeUsernameFromHttpUrl();

  boolean isCleanCredHelperScript();

  boolean isProvideCredHelper();

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

  boolean shouldIgnoreCheckoutRulesPostfixCheck();

  @NotNull
  Map<String, String> getGitTraceEnv();

  @NotNull
  Map<String, Long> getCustomRecoverableMessages();

  int getShallowCloneDepth();

  int getSubmodulesShallowDepth();

  boolean isNoShowForcedUpdates();

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