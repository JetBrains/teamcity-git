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

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.ProxyHTTP;
import gnu.trove.TObjectHashingStrategy;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.util.DiagnosticUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.BranchSupport;
import jetbrains.buildServer.vcs.VcsPersonalSupport;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.commons.codec.Decoder;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements ServerPluginConfig {

  private final static long SECOND = 1000L;
  private final static long MINUTE = 60 * SECOND;
  private final static long HOUR = 60 * MINUTE;
  private final static long DAY = 24 * HOUR;


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
    return TeamCityProperties.getInteger("teamcity.git.fetch.timeout", 600);
  }


  public int getCloneTimeout() {
    return TeamCityProperties.getInteger("teamcity.git.clone.timeout", 600);
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

  public String getFetchClasspath() {
    return ClasspathUtil.composeClasspath(new Class[]{
      Fetcher.class,
      VcsRoot.class,
      ProgressMonitor.class,
      VcsPersonalSupport.class,
      Logger.class,
      Settings.class,
      JSch.class,
      Decoder.class,
      TObjectHashingStrategy.class,
      BranchSupport.class,
      EncryptUtil.class,
      DiagnosticUtil.class,
      FileUtil.class
    }, null, null);
  }


  public String getFetcherClassName() {
    return Fetcher.class.getName();
  }

  public int getFixedSubmoduleCommitSearchDepth() {
    return TeamCityProperties.getInteger("teamcity.server.git.fixed.submodule.commit.search.depth", 100);
  }

  public int getIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.idle.timeout.seconds", DEFAULT_IDLE_TIMEOUT);
  }


  public long getMirrorExpirationTimeoutMillis() {
    int days = TeamCityProperties.getInteger("teamcity.git.mirror.expiration.timeout.days", 7);
    return days * DAY;
  }

  @NotNull
  public List<String> getProxySettingsForSeparateProcess() {
    List<String> proxySettings = new ArrayList<String>();
    addHttpProxyHost(proxySettings);
    addHttpProxyPort(proxySettings);
    addHttpNonProxyHosts(proxySettings);
    addHttpsProxyHost(proxySettings);
    addHttpsProxyPort(proxySettings);
    return proxySettings;
  }

  public Proxy getJschProxy() {
    String httpProxyHost = TeamCityProperties.getProperty("http.proxyHost");
    int httpProxyPort = TeamCityProperties.getInteger("http.proxyPort", ProxyHTTP.getDefaultPort());
    if (isEmpty(httpProxyHost))
      return null;
    return new ProxyHTTP(httpProxyHost, httpProxyPort);
  }

  private void addHttpProxyHost(@NotNull final List<String> proxySettings) {
    String httpProxyHost = TeamCityProperties.getProperty("http.proxyHost");
    if (!isEmpty(httpProxyHost))
      proxySettings.add("-Dhttp.proxyHost=" + httpProxyHost);
  }

  private void addHttpProxyPort(List<String> proxySettings) {
    int httpProxyPort = TeamCityProperties.getInteger("http.proxyPort", -1);
    if (httpProxyPort != -1)
      proxySettings.add("-Dhttp.proxyPort=" + httpProxyPort);
  }

  private void addHttpNonProxyHosts(List<String> proxySettings) {
    String httpNonProxyHosts = TeamCityProperties.getProperty("http.nonProxyHosts");
    if (!isEmpty(httpNonProxyHosts))
      proxySettings.add("-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"");
  }

  private void addHttpsProxyHost(List<String> proxySettings) {
    String httpsProxyHost = TeamCityProperties.getProperty("https.proxyHost");
    if (!isEmpty(httpsProxyHost))
      proxySettings.add("-Dhttps.proxyHost=" + httpsProxyHost);
  }

  private void addHttpsProxyPort(List<String> proxySettings) {
    int httpsProxyPort = TeamCityProperties.getInteger("https.proxyPort", -1);
    if (httpsProxyPort != -1)
      proxySettings.add("-Dhttps.proxyPort=" + httpsProxyPort);
  }

  @NotNull
  public String getMonitoringDirName() {
    return "monitoring";
  }

  public int getMonitoringExpirationTimeoutHours() {
    return TeamCityProperties.getInteger("teamcity.git.monitoring.expiration.timeout.hours", 24);
  }

  public boolean alwaysDoFetchOnGetCurrentVersion() {
    return TeamCityProperties.getBoolean("teamcity.git.always.do.fetch.on.get.current.version");
  }

  public boolean alwaysCheckCiphers() {
    return TeamCityProperties.getBoolean("teamcity.git.always.check.ciphers");
  }
}
