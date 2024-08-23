

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.jcraft.jsch.Proxy;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

import static jetbrains.buildServer.util.Util.map;

/**
 * @author dmitry.neverov
 */
public class PluginConfigBuilder {

  private PluginConfigImpl myDelegate;
  private Boolean mySeparateProcessForFetch;
  private Boolean mySeparateProcessForPatch;
  private Boolean myRunNativeGC;
  private Boolean myRunJGitGC;
  private String  myPathToGit;
  private String  myFetchClassPath;
  private String  myFetcherClassName;
  private Integer myFixedSubmoduleCommitSearchDepth;
  private Integer myIdleTimeoutSeconds;
  private Integer myFetchTimeoutSeconds;
  private Integer myCurrentStateTimeoutSeconds;
  private Long myMirrorExpirationTimeoutMillis;
  private ServerPaths myPaths;
  private File myDotBuildServerDir;
  private Map<String, String> myFetcherProperties = new HashMap<String, String>();
  private int myGetConnectionRetryAttempts = -1;
  private long myConnectionRetryIntervalMillis = -1;
  private Integer myStreamFileThreshold = null;
  private String myPatchBuilderClassName;
  private String myPatchClassPath;
  private String myFetchProcessMaxMemory;
  private boolean myUsePackHeuristic;
  private boolean myFailLabelingWhenPackHeuristicsFail;
  private Integer myPushIdleTimeoutSeconds;
  private Boolean myPersistentCacheEnabled;
  private Integer myMapFullPathRevisionCacheSize;
  private TempFiles myTempFiles;
  private Boolean myNewConnectionForPrune;
  private Boolean myIgnoreMissingRemoteRef;
  private Integer myMergeRetryAttempts;
  private Boolean myRunInPlaceGc;
  private Boolean myReportPerParentChangedFiles;
  private boolean myFetchAllRefsEnabled;
  private float myFetchRemoteBranchesFactor;
  private int myFetchRemoteBranchesThreshold;

  public static PluginConfigBuilder pluginConfig() {
    return new PluginConfigBuilder();
  }

  public PluginConfigBuilder() {
    myFetchRemoteBranchesThreshold = PluginConfigImpl.FETCH_REMOTE_BRANCHES_THRESHOLD_DEFAULT;
  }

  public PluginConfigBuilder(@NotNull ServerPaths paths) {
    this();
    myPaths = paths;
  }

  public ServerPluginConfig build() {
    if (myPaths == null && myDotBuildServerDir == null && myTempFiles == null)
      throw new IllegalArgumentException("Either ServerPaths or .BuildServer dir must be set");
    if (myPaths != null) {
      myDelegate = new PluginConfigImpl(myPaths);
    } else if (myDotBuildServerDir != null) {
      myDelegate = new PluginConfigImpl(new ServerPaths(myDotBuildServerDir.getAbsolutePath()));
    } else {
      try {
        myDelegate = new PluginConfigImpl(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return new ServerPluginConfig() {

      @NotNull
      public File getCachesDir() {
        return myDelegate.getCachesDir();
      }

      public int getStreamFileThresholdMb() {
        return myStreamFileThreshold != null ? myStreamFileThreshold : myDelegate.getStreamFileThresholdMb();
      }

      public int getFetchTimeoutSeconds() {
        return myFetchTimeoutSeconds != null ? myFetchTimeoutSeconds : myDelegate.getFetchTimeoutSeconds();
      }

      @Override
      public int getPushTimeoutSeconds() {
        return myPushIdleTimeoutSeconds != null ? myPushIdleTimeoutSeconds : myDelegate.getPushTimeoutSeconds();
      }

      public int getRepositoryStateTimeoutSeconds() {
        return myCurrentStateTimeoutSeconds != null ? myCurrentStateTimeoutSeconds : myDelegate.getRepositoryStateTimeoutSeconds();
      }

      public int getPatchProcessIdleTimeoutSeconds() {
        return myDelegate.getPatchProcessIdleTimeoutSeconds();
      }

      public String getFetchProcessJavaPath() {
        return myDelegate.getFetchProcessJavaPath();
      }

      public long freeRAM() {
        return 1024 * 1024 * 1024 + 1024; // a little more then 1GB
      }

      @NotNull
      public String getFetchProcessMaxMemory() {
        return myFetchProcessMaxMemory != null ? myFetchProcessMaxMemory : myDelegate.getFetchProcessMaxMemory();
      }

      public String getGcProcessMaxMemory() {
        return myDelegate.getGcProcessMaxMemory();
      }

      @Nullable
      public String getExplicitFetchProcessMaxMemory() {
        return myDelegate.getExplicitFetchProcessMaxMemory();
      }

      @Nullable
      public String getMaximumFetchProcessMaxMemory() {
        return myDelegate.getMaximumFetchProcessMaxMemory();
      }

      @Override
      public float getFetchProcessMemoryMultiplyFactor() {
        return myDelegate.getFetchProcessMemoryMultiplyFactor();
      }

      public boolean isSeparateProcessForFetch() {
        return mySeparateProcessForFetch != null ? mySeparateProcessForFetch : myDelegate.isSeparateProcessForFetch();
      }

      public boolean isSeparateProcessForPatch() {
        return mySeparateProcessForPatch != null ? mySeparateProcessForPatch : myDelegate.isSeparateProcessForPatch();
      }

      public boolean isRunNativeGC() {
        return myRunNativeGC != null ? myRunNativeGC : myDelegate.isRunNativeGC();
      }

      public boolean isRunJGitGC() {
        return myRunJGitGC != null ? myRunJGitGC : myDelegate.isRunJGitGC();
      }

      public String getPathToGit() {
        return myPathToGit != null ? myPathToGit : myDelegate.getPathToGit();
      }

      public int getNativeGCQuotaMinutes() {
        return myDelegate.getNativeGCQuotaMinutes();
      }

      public String getFetchClasspath() {
        return myFetchClassPath != null ? myFetchClassPath : myDelegate.getFetchClasspath();
      }

      public String getFetcherClassName() {
        return myFetcherClassName != null ? myFetcherClassName : myDelegate.getFetcherClassName();
      }

      public String getPatchClasspath() {
        return myPatchClassPath != null ? myPatchClassPath : myDelegate.getPatchClasspath();
      }

      public String getPatchBuilderClassName() {
        return myPatchBuilderClassName != null ? myPatchBuilderClassName : myDelegate.getPatchBuilderClassName();
      }

      public boolean passEnvToChildProcess() {
        return myDelegate.passEnvToChildProcess();
      }

      public int getFixedSubmoduleCommitSearchDepth() {
        return myFixedSubmoduleCommitSearchDepth != null ? myFixedSubmoduleCommitSearchDepth : myDelegate.getFixedSubmoduleCommitSearchDepth();
      }

      public int getIdleTimeoutSeconds() {
        return myIdleTimeoutSeconds != null ? myIdleTimeoutSeconds : myDelegate.getIdleTimeoutSeconds();
      }

      public long getMirrorExpirationTimeoutMillis() {
        return myMirrorExpirationTimeoutMillis != null ? myMirrorExpirationTimeoutMillis : myDelegate.getMirrorExpirationTimeoutMillis();
      }

      @NotNull
      public List<String> getOptionsForSeparateProcess() {
        return myDelegate.getOptionsForSeparateProcess();
      }

      @Nullable
      public Proxy getJschProxy() {
        return null;
      }

      @NotNull
      public String getMonitoringDirName() {
        return myDelegate.getMonitoringDirName();
      }

      public int getMonitoringExpirationTimeoutHours() {
        return myDelegate.getMonitoringExpirationTimeoutHours();
      }

      @Override
      public long getMonitoringFileThresholdMillis() {
        return myDelegate.getMonitoringFileThresholdMillis();
      }

      public boolean alwaysCheckCiphers() {
        return false;
      }

      public boolean verboseGetContentLog() {
        return false;
      }

      public boolean verboseTreeWalkLog() {
        return false;
      }

      public int getMapFullPathRevisionCacheSize() {
        return myMapFullPathRevisionCacheSize != null ? myMapFullPathRevisionCacheSize : myDelegate.getMapFullPathRevisionCacheSize();
      }

      public long getConnectionRetryIntervalMillis() {
        return myConnectionRetryIntervalMillis != -1 ? myConnectionRetryIntervalMillis : myDelegate.getConnectionRetryIntervalMillis();
      }

      public int getConnectionRetryAttempts() {
        return myGetConnectionRetryAttempts != -1 ? myGetConnectionRetryAttempts : myDelegate.getConnectionRetryAttempts();
      }

      public boolean ignoreFetchedCommits() {
        return false;
      }

      @Nullable
      public CronExpression getCleanupCronExpression() {
        return null;
      }

      @NotNull
      public Map<String, String> getFetcherProperties() {
        return myFetcherProperties;
      }

      public int getHttpsSoLinger() {
        return 0;
      }

      public int getListFilesTTLSeconds() {
        return 0;
      }

      @NotNull
      public String getHttpConnectionFactory() {
        return "httpClient";
      }

      @NotNull
      public String getHttpConnectionSslProtocol() {
        return myDelegate.getHttpConnectionSslProtocol();
      }

      @NotNull
      public List<String> getAmazonHosts() {
        return myDelegate.getAmazonHosts();
      }

      public boolean useTagPackHeuristics() {
        return myUsePackHeuristic;
      }

      public boolean analyzeTagsInPackHeuristics() {
        return myDelegate.analyzeTagsInPackHeuristics();
      }

      @Override
      public boolean checkLabeledCommitIsInRemoteRepository() {
        return myDelegate.checkLabeledCommitIsInRemoteRepository();
      }

      @Override
      public boolean failLabelingWhenPackHeuristicsFails() {
        return myFailLabelingWhenPackHeuristicsFail || myDelegate.failLabelingWhenPackHeuristicsFails();
      }

      @Override
      public boolean persistentCacheEnabled() {
        if (myPersistentCacheEnabled != null)
          return myPersistentCacheEnabled;
        return myDelegate.persistentCacheEnabled();
      }

      @Override
      public boolean logRemoteRefs() {
        return false;
      }

      @Override
      public long getAccessTimeUpdateRateMinutes() {
        return myDelegate.getAccessTimeUpdateRateMinutes();
      }

      public boolean ignoreMissingRemoteRef() {
        if (myIgnoreMissingRemoteRef != null)
          return myIgnoreMissingRemoteRef;
        return myDelegate.ignoreMissingRemoteRef();
      }

      @Override
      public int getMergeRetryAttempts() {
        if (myMergeRetryAttempts != null)
          return myMergeRetryAttempts;
        return myDelegate.getMergeRetryAttempts();
      }

      @Override
      public boolean runInPlaceGc() {
        if (myRunInPlaceGc != null)
          return myRunInPlaceGc;
        return myDelegate.runInPlaceGc();
      }

      @Override
      public int getRepackIdleTimeoutSeconds() {
        return myDelegate.getRepackIdleTimeoutSeconds();
      }

      @NotNull
      @Override
      public List<String> getRepackCommandArguments() {
        return myDelegate.getRepackCommandArguments();
      }

      @Override
      public int getPackRefsIdleTimeoutSeconds() {
        return myDelegate.getPackRefsIdleTimeoutSeconds();
      }

      @Override
      public boolean treatMissingBranchTipAsRecoverableError() {
        return myDelegate.treatMissingBranchTipAsRecoverableError();
      }

      @NotNull
      @Override
      public List<String> getRecoverableFetchErrorMessages() {
        return myDelegate.getRecoverableFetchErrorMessages();
      }

      @Override
      public boolean reportPerParentChangedFiles() {
        return myReportPerParentChangedFiles != null ? myReportPerParentChangedFiles : myDelegate.reportPerParentChangedFiles();
      }

      @Override
      public boolean shouldSetSubmoduleUserInAbsoluteUrls() {
        return myDelegate.shouldSetSubmoduleUserInAbsoluteUrls();
      }

      @Override
      public boolean fetchAllRefsEnabled() {
        return myFetchAllRefsEnabled;
      }

      @Override
      public long repositoryWriteLockTimeout() {
        return 0;
      }

      @Override
      public boolean refreshObjectDatabaseAfterFetch() {
        return true;
      }

      @Override
      public float fetchRemoteBranchesFactor() {
        return myFetchRemoteBranchesFactor;
      }

      @Override
      public int fetchRemoteBranchesThreshold() {
        return myFetchRemoteBranchesThreshold;
      }

      @NotNull
      @Override
      public Map<String, String> getGitTraceEnv() {
        return Collections.emptyMap();
      }

      @Nullable
      @Override
      public String getGitOutputCharsetName() {
        return null;
      }

      @Override
      public boolean downloadLfsObjectsForPatch() {
        return true;
      }

      @NotNull
      @Override
      public List<String> getFetchDurationMetricRepos() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public File getSslDir() {
        return new File("");
      }
    };
  }


  PluginConfigBuilder setSeparateProcessForFetch(boolean separateProcess) {
    mySeparateProcessForFetch = separateProcess;
    return this;
  }


  PluginConfigBuilder setSeparateProcessForPatch(boolean useSeparateProcessForPatch) {
    mySeparateProcessForPatch = useSeparateProcessForPatch;
    return this;
  }


  PluginConfigBuilder setRunNativeGC(boolean run) {
    myRunNativeGC = run;
    return this;
  }


  PluginConfigBuilder setRunJGitGC(boolean run) {
    myRunJGitGC = run;
    return this;
  }


  PluginConfigBuilder setPathToGit(String path) {
    myPathToGit = path;
    return this;
  }


  PluginConfigBuilder setFetchClasspath(String classpath) {
    myFetchClassPath = classpath;
    return this;
  }


  PluginConfigBuilder setFetcherClassName(String className) {
    myFetcherClassName = className;
    return this;
  }


  PluginConfigBuilder setFetchProcessMaxMemory(String fetchProcessMaxMemory) {
    myFetchProcessMaxMemory = fetchProcessMaxMemory;
    return this;
  }


  public PluginConfigBuilder setFixedSubmoduleCommitSearchDepth(Integer depth) {
    myFixedSubmoduleCommitSearchDepth = depth;
    return this;
  }


  public PluginConfigBuilder setIdleTimeoutSeconds(int timeout) {
    myIdleTimeoutSeconds = timeout;
    return this;
  }


  public PluginConfigBuilder setMirrorExpirationTimeoutMillis(long timeoutMillis) {
    myMirrorExpirationTimeoutMillis = timeoutMillis;
    return this;
  }


  public PluginConfigBuilder withDotBuildServerDir(@NotNull File dotBuildServer) {
    myDotBuildServerDir = dotBuildServer;
    return this;
  }

  public PluginConfigBuilder withFetcherProperties(@NotNull String... props) {
    myFetcherProperties.putAll(map(props));
    return this;
  }

  public PluginConfigBuilder withGetConnectionRetryAttempts(int retryAttemptsCount) {
    myGetConnectionRetryAttempts = retryAttemptsCount;
    return this;
  }

  public PluginConfigBuilder withConnectionRetryIntervalMillis(long intervalMillis) {
    myConnectionRetryIntervalMillis = intervalMillis;
    return this;
  }

  public PluginConfigBuilder setStreamFileThreshold(final Integer streamFileThreshold) {
    myStreamFileThreshold = streamFileThreshold;
    return this;
  }

  public PluginConfigBuilder setPatchClassPath(String classPath) {
    myPatchClassPath = classPath;
    return this;
  }

  public PluginConfigBuilder setPatchBuilderClassName(String className) {
    myPatchBuilderClassName = className;
    return this;
  }


  public PluginConfigBuilder setFetchTimeout(final Integer fetchTimeoutSeconds) {
    myFetchTimeoutSeconds = fetchTimeoutSeconds;
    return this;
  }

  public PluginConfigBuilder setCurrentStateTimeoutSeconds(final Integer currentStateTimeoutSeconds) {
    myCurrentStateTimeoutSeconds = currentStateTimeoutSeconds;
    return this;
  }

  public PluginConfigBuilder setPaths(@NotNull ServerPaths paths) {
    myPaths = paths;
    return this;
  }


  public PluginConfigBuilder setUsePackHeuristic(boolean usePackHeuristic) {
    myUsePackHeuristic = usePackHeuristic;
    return this;
  }


  public PluginConfigBuilder setFailLabelingWhenPackHeuristicsFail(boolean doFail) {
    myFailLabelingWhenPackHeuristicsFail = doFail;
    return this;
  }


  public PluginConfigBuilder setPushIdleTimeoutSeconds(int timeoutSeconds) {
    myPushIdleTimeoutSeconds = timeoutSeconds;
    return this;
  }


  public PluginConfigBuilder setPersistentCacheEnabled(boolean persistentCacheEnabled) {
    myPersistentCacheEnabled = persistentCacheEnabled;
    return this;
  }


  public PluginConfigBuilder setMapFullPathRevisionCacheSize(int mapFullPathRevisionCacheSize) {
    myMapFullPathRevisionCacheSize = mapFullPathRevisionCacheSize;
    return this;
  }


  public PluginConfigBuilder setTempFiles(final TempFiles tempFiles) {
    myTempFiles = tempFiles;
    return this;
  }


  PluginConfigBuilder setNewConnectionForPrune(boolean newConnectionForPrune) {
    myNewConnectionForPrune = newConnectionForPrune;
    return this;
  }

  PluginConfigBuilder setIgnoreMissingRemoteRef(boolean ignoreMissingRemoteRef) {
    myIgnoreMissingRemoteRef = ignoreMissingRemoteRef;
    return this;
  }

  PluginConfigBuilder setMergeRetryAttempts(@Nullable Integer retryAttempts) {
    myMergeRetryAttempts = retryAttempts;
    return this;
  }

  PluginConfigBuilder setRunInPlaceGc(boolean runInPlaceGc) {
    myRunInPlaceGc = runInPlaceGc;
    return this;
  }

  PluginConfigBuilder setReportPerParentChangedFiles(boolean report) {
    myReportPerParentChangedFiles = report;
    return this;
  }

  PluginConfigBuilder setFetchAllRefsEnabled(final boolean fetchAllRefsEnabled) {
    myFetchAllRefsEnabled = fetchAllRefsEnabled;
    return this;
  }

  PluginConfigBuilder setFetchRemoteBranchesFactor(float factor) {
    myFetchRemoteBranchesFactor = factor;
    return this;
  }

  PluginConfigBuilder setFetchRemoteBranchesThreshold(int threshold) {
    myFetchRemoteBranchesThreshold = threshold;
    return this;
  }
}