/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.jcraft.jsch.Proxy;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.util.Util.map;

/**
 * @author dmitry.neverov
 */
public class PluginConfigBuilder {

  private PluginConfigImpl myDelegate;
  private Boolean mySeparateProcessForFetch;
  private Boolean myRunNativeGC;
  private String  myPathToGit;
  private String  myFetchClassPath;
  private String  myFetcherClassName;
  private Integer myFixedSubmoduleCommitSearchDepth;
  private Integer myIdleTimeoutSeconds;
  private Long myMirrorExpirationTimeoutMillis;
  private int myNumberOfCommitsWhenFromVersionNotFound = -1;
  private ServerPaths myPaths;
  private File myDotBuildServerDir;
  private Map<String, String> myFetcherProperties = new HashMap<String, String>();
  private boolean myUsePerBranchFetch;
  private int myGetConnectionRetryAttempts = -1;

  public static PluginConfigBuilder pluginConfig() {
    return new PluginConfigBuilder();
  }

  public PluginConfigBuilder() {
  }

  public PluginConfigBuilder(@NotNull ServerPaths paths) {
    myPaths = paths;
  }

  public ServerPluginConfig build() {
    if (myPaths == null && myDotBuildServerDir == null)
      throw new IllegalArgumentException("Either ServerPaths or .BuildServer dir must be set");
    myDelegate = myPaths != null ? new PluginConfigImpl(myPaths) : new PluginConfigImpl(new ServerPaths(myDotBuildServerDir.getAbsolutePath()));
    return new ServerPluginConfig() {
      @NotNull
      public File getCachesDir() {
        return myDelegate.getCachesDir();
      }

      public int getStreamFileThreshold() {
        return myDelegate.getStreamFileThreshold();
      }

      public int getFetchTimeout() {
        return myDelegate.getFetchTimeout();
      }

      public boolean isPrintDebugInfoOnEachCommit() {
        return myDelegate.isPrintDebugInfoOnEachCommit();
      }

      public String getFetchProcessJavaPath() {
        return myDelegate.getFetchProcessJavaPath();
      }

      public String getFetchProcessMaxMemory() {
        return myDelegate.getFetchProcessMaxMemory();
      }

      public boolean isSeparateProcessForFetch() {
        return mySeparateProcessForFetch != null ? mySeparateProcessForFetch : myDelegate.isSeparateProcessForFetch();
      }

      public boolean isRunNativeGC() {
        return myRunNativeGC != null ? myRunNativeGC : myDelegate.isRunNativeGC();
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
      public List<String> getProxySettingsForSeparateProcess() {
        return Collections.emptyList();
      }

      @Nullable
      public Proxy getJschProxy() {
        return null;
      }

      public int getNumberOfCommitsWhenFromVersionNotFound() {
        return myNumberOfCommitsWhenFromVersionNotFound != -1 ? myNumberOfCommitsWhenFromVersionNotFound : myDelegate.getNumberOfCommitsWhenFromVersionNotFound();
      }

      @NotNull
      public String getMonitoringDirName() {
        return myDelegate.getMonitoringDirName();
      }

      public int getMonitoringExpirationTimeoutHours() {
        return myDelegate.getMonitoringExpirationTimeoutHours();
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
        return 100;
      }

      public long getConnectionRetryIntervalMillis() {
        return myDelegate.getConnectionRetryIntervalMillis();
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

      public boolean usePerBranchFetch() {
        return myUsePerBranchFetch;
      }

      public int getHttpsSoLinger() {
        return 0;
      }

      public int getListFilesTTLSeconds() {
        return 0;
      }
    };
  }


  PluginConfigBuilder setSeparateProcessForFetch(boolean separateProcess) {
    mySeparateProcessForFetch = separateProcess;
    return this;
  }


  PluginConfigBuilder setRunNativeGC(boolean run) {
    myRunNativeGC = run;
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


  public PluginConfigBuilder setNumberOfCommitsWhenFromVersionNotFound(int numberOfCommits) {
    myNumberOfCommitsWhenFromVersionNotFound = numberOfCommits;
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

  public PluginConfigBuilder withPerBranchFetch(boolean usePerBranchFetch) {
    myUsePerBranchFetch = usePerBranchFetch;
    return this;
  }

  public PluginConfigBuilder withGetConnectionRetryAttempts(int retryAttemptsCount) {
    myGetConnectionRetryAttempts = retryAttemptsCount;
    return this;
  }
}
