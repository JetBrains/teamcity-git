

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.jcraft.jsch.Proxy;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

/**
 * @author dmitry.neverov
 */
public interface ServerPluginConfig extends PluginConfig {

  /**
   * @return streaming threshold in megabytes (ensures it is a positive number)
   */
  int getStreamFileThresholdMb();

  int getFetchTimeoutSeconds();

  int getPushTimeoutSeconds();

  int getRepositoryStateTimeoutSeconds();

  int getPatchProcessIdleTimeoutSeconds();

  int getPruneTimeoutSeconds();

  String getFetchProcessJavaPath();


  @NotNull
  String getFetchProcessMaxMemory();

  String getGcProcessMaxMemory();

  @Nullable
  String getExplicitFetchProcessMaxMemory();

  @Nullable
  String getMaximumFetchProcessMaxMemory();

  float getFetchProcessMemoryMultiplyFactor();

  boolean isSeparateProcessForFetch();

  boolean isSeparateProcessForPatch();

  boolean isRunNativeGC();

  boolean isRunJGitGC();


  int getNativeGCQuotaMinutes();


  String getFetchClasspath();


  String getFetcherClassName();

  String getPatchClasspath();

  String getPatchBuilderClassName();

  public boolean passEnvToChildProcess();

  int getFixedSubmoduleCommitSearchDepth();


  long getMirrorExpirationTimeoutMillis();

  @NotNull
  List<String> getOptionsForSeparateProcess();

  /**
   * @return proxy for jsch of null if no proxy required
   */
  @Nullable
  Proxy getJschProxy();

  @NotNull
  String getMonitoringDirName();

  int getMonitoringExpirationTimeoutHours();

  long getMonitoringFileThresholdMillis();

  boolean alwaysCheckCiphers();

  boolean verboseGetContentLog();

  boolean verboseTreeWalkLog();

  int getMapFullPathRevisionCacheSize();

  long getConnectionRetryIntervalMillis();

  int getConnectionRetryAttempts();

  boolean ignoreFetchedCommits();

  @Nullable
  CronExpression getCleanupCronExpression();

  @NotNull
  Map<String, String> getFetcherProperties();

  int getHttpsSoLinger();

  int getListFilesTTLSeconds();

  @NotNull
  String getHttpConnectionFactory();

  @NotNull
  String getHttpConnectionSslProtocol();

  @NotNull
  List<String> getAmazonHosts();

  boolean useTagPackHeuristics();

  boolean analyzeTagsInPackHeuristics();

  boolean checkLabeledCommitIsInRemoteRepository();

  boolean failLabelingWhenPackHeuristicsFails();

  boolean persistentCacheEnabled();

  boolean logRemoteRefs();

  long getAccessTimeUpdateRateMinutes();

  boolean ignoreMissingRemoteRef();

  int getMergeRetryAttempts();

  boolean runInPlaceGc();

  int getRepackIdleTimeoutSeconds();

  @NotNull
  List<String> getRepackCommandArguments();

  int getPackRefsIdleTimeoutSeconds();

  boolean treatMissingBranchTipAsRecoverableError();

  boolean reportPerParentChangedFiles();

  boolean shouldSetSubmoduleUserInAbsoluteUrls();

  @NotNull
  List<String> getRecoverableFetchErrorMessages();

  boolean fetchAllRefsEnabled();

  long repositoryWriteLockTimeout();

  boolean refreshObjectDatabaseAfterFetch();

  float fetchRemoteBranchesFactor();

  int fetchRemoteBranchesThreshold();

  @NotNull
  Map<String, String> getGitTraceEnv();

  @NotNull
  Collection<String> getCustomRecoverableMessages();

  boolean downloadLfsObjectsForPatch();

  @NotNull
  List<String> getFetchDurationMetricRepos();

  @NotNull
  File getSslDir();

  Collection<String> getPrefixesToCollectOnlyHeads();
}