/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.List;

/**
 * @author dmitry.neverov
 */
public interface ServerPluginConfig extends PluginConfig {

  int getCurrentVersionCacheSize();


  int getStreamFileThreshold();


  int getFetchTimeout();


  int getCloneTimeout();


  boolean isPrintDebugInfoOnEachCommit();


  String getFetchProcessJavaPath();


  String getFetchProcessMaxMemory();


  boolean isSeparateProcessForFetch();


  boolean isRunNativeGC();


  int getNativeGCQuotaMinutes();


  String getFetchClasspath();


  String getFetcherClassName();


  int getFixedSubmoduleCommitSearchDepth();


  long getMirrorExpirationTimeoutMillis();

  @NotNull
  List<String> getProxySettingsForSeparateProcess();

  /**
   * @return proxy for jsch of null if no proxy required
   */
  @Nullable
  Proxy getJschProxy();

  @NotNull
  String getMonitoringDirName();

  int getMonitoringExpirationTimeoutHours();

  boolean alwaysDoFetchOnGetCurrentVersion();

  boolean alwaysCheckCiphers();
}
