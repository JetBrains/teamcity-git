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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.jcraft.jsch.Proxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

import java.util.List;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public interface ServerPluginConfig extends PluginConfig {

  /**
   * @return streaming threshold in megabytes (ensures it is a positive number)
   */
  int getStreamFileThresholdMb();

  int getFetchTimeout();

  int getPushTimeoutSeconds();

  int getRepositoryStateTimeoutSeconds();

  int getPatchProcessIdleTimeoutSeconds();

  String getFetchProcessJavaPath();


  String getFetchProcessMaxMemory();

  String getGcProcessMaxMemory();

  @Nullable
  String getExplicitFetchProcessMaxMemory();


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

  //Seems like fetch per branch is never required, will remove it if a single fetch works fine
  boolean usePerBranchFetch();

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

  boolean createNewConnectionForPrune();

  long getAccessTimeUpdateRateMinutes();

  boolean ignoreMissingRemoteRef();

  int getMergeRetryAttempts();

  boolean runInPlaceGc();

  int getRepackIdleTimeoutSeconds();

  int getPackRefsIdleTimeoutSeconds();

  boolean treatMissingBranchTipAsRecoverableError();

  boolean reportPerParentChangedFiles();

  boolean shouldSetSubmoduleUserInAbsoluteUrls();

  @NotNull
  List<String> getRecoverableFetchErrorMessages();
}
