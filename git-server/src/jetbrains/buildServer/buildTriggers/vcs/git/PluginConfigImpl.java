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

import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements PluginConfig {

  private final File myCachesDir;


  public PluginConfigImpl(@NotNull  final ServerPaths serverPaths) {
    myCachesDir = new File(serverPaths.getCachesDir(), "git");
  }


  @NotNull
  public File getCachesDir() {
    return myCachesDir;
  }


  public int getCurrentVersionCacheSize() {
    return TeamCityProperties.getInteger("teamcity.git.current.version.cache.size", 100);
  }


  public int getStreamFileThreshold() {
    if (isSeparateProcessForFetch()) {
      return TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", 128);
    } else {
      return TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", 64);
    }
  }


  public int getFetchTimeout() {
    return TeamCityProperties.getInteger("teamcity.git.fetch.timeout", 18000);
  }


  public int getCloneTimeout() {
    return TeamCityProperties.getInteger("teamcity.git.clone.timeout", 18000);
  }


  public boolean isPrintDebugInfoOnEachCommit() {
    return TeamCityProperties.getBoolean("teamcity.git.commit.debug.info");
  }


  public String getFetchProcessJavaPath() {
    final String jdkHome = System.getProperty("java.home");
    File defaultJavaExec = new File(jdkHome.replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "java");
    return TeamCityProperties.getProperty("teamcity.git.fetch.process.java.exec", defaultJavaExec.getAbsolutePath());
  }


  public String getFetchProcessMaxMemory() {
    return TeamCityProperties.getProperty("teamcity.git.fetch.process.max.memory", "512M");
  }


  public boolean isSeparateProcessForFetch() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.fetch.separate.process");
  }


  public boolean isRunNativeGC() {
    return TeamCityProperties.getBoolean("teamcity.server.git.gc.enabled");
  }

  public String getPathToGit() {
    return TeamCityProperties.getProperty("teamcity.server.git.executable.path", "git");
  }

  public int getNativeGCQuotaMinutes() {
    return TeamCityProperties.getInteger("teamcity.server.git.gc.quota.minutes", 60);
  }
}
