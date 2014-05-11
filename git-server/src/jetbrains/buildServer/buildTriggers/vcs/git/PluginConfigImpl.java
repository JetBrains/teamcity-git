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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.*;
import gnu.trove.TObjectHashingStrategy;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.serverSide.CachePaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.DiagnosticUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsPersonalSupport;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.commons.codec.Decoder;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Layout;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.ParseException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements ServerPluginConfig {

  public static final String TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS = "teamcity.git.idle.timeout.seconds";
  private static final String TEAMCITY_GIT_SSH_PROXY_TYPE = "teamcity.git.sshProxyType";
  private static final String TEAMCITY_GIT_SSH_PROXY_HOST = "teamcity.git.sshProxyHost";
  private static final String TEAMCITY_GIT_SSH_PROXY_PORT = "teamcity.git.sshProxyPort";
  private static final String TEAMCITY_GIT_ALWAYS_CHECK_CIPHERS = "teamcity.git.always.check.ciphers";

  private final static Logger LOG = Logger.getInstance(PluginConfigImpl.class.getName());
  private final static int GB = 1024 * 1024 * 1024;//bytes
  private final File myCachesDir;
  private final Set<String> myFetcherPropertyNames = setOf(TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS,
                                                           TEAMCITY_GIT_SSH_PROXY_TYPE,
                                                           TEAMCITY_GIT_SSH_PROXY_HOST,
                                                           TEAMCITY_GIT_SSH_PROXY_PORT,
                                                           TEAMCITY_GIT_ALWAYS_CHECK_CIPHERS);

  public PluginConfigImpl() {
    myCachesDir = null;
  }

  public PluginConfigImpl(@NotNull final CachePaths paths) {
    myCachesDir = paths.getCacheDirectory("git");
  }


  public static boolean isTeamcitySshKeysEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.enableTeamcitySshKeys");
  }


  @NotNull
  public File getCachesDir() {
    if (myCachesDir == null)
      throw new IllegalStateException("Caches dir is not initialized");
    return myCachesDir;
  }


  public int getStreamFileThreshold() {
    return TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", 128);
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
    String maxMemory = TeamCityProperties.getProperty("teamcity.git.fetch.process.max.memory");
    if (!isEmpty(maxMemory))
      return maxMemory;
    try {
      Class.forName("com.sun.management.OperatingSystemMXBean");
    } catch (ClassNotFoundException e) {
      return "512M";
    }
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
      long freeRAM = ((com.sun.management.OperatingSystemMXBean) osBean).getFreePhysicalMemorySize();
      if (freeRAM > GB)
        return "1024M";
    }
    return "512M";
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
    Set<Class<?>> clazzez = new HashSet<Class<?>>(Arrays.asList(
      Fetcher.class,
      VcsRoot.class,
      ProgressMonitor.class,
      VcsPersonalSupport.class,
      Logger.class,
      GitVcsRoot.class,
      JSch.class,
      Decoder.class,
      TObjectHashingStrategy.class,
      EncryptUtil.class,
      DiagnosticUtil.class,
      FileUtil.class,
      Layout.class,
      VcsException.class,
      BasicConfigurator.class,
      HttpClientConnectionFactory.class,
      HttpClient.class,
      LogFactory.class,
      HttpEntity.class
    ));

    Collections.addAll(clazzez, GitVcsSupport.class.getInterfaces());
    return ClasspathUtil.composeClasspath(clazzez.toArray(new Class[clazzez.size()]), null, null);
  }


  public String getFetcherClassName() {
    return Fetcher.class.getName();
  }

  public int getFixedSubmoduleCommitSearchDepth() {
    return TeamCityProperties.getInteger("teamcity.server.git.fixed.submodule.commit.search.depth", 100);
  }

  public int getIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger(TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS, DEFAULT_IDLE_TIMEOUT);
  }

  public int getFetchTimeout() {
    int deprecatedFetchTimeout = TeamCityProperties.getInteger("teamcity.git.fetch.timeout", DEFAULT_IDLE_TIMEOUT);
    int idleTimeout = getIdleTimeoutSeconds();
    if (deprecatedFetchTimeout > idleTimeout)
      return deprecatedFetchTimeout;
    return idleTimeout;
  }

  public long getMirrorExpirationTimeoutMillis() {
    int days = TeamCityProperties.getInteger("teamcity.git.mirror.expiration.timeout.days", 7);
    return days * Dates.ONE_DAY;
  }

  @NotNull
  public List<String> getProxySettingsForSeparateProcess() {
    List<String> proxySettings = new ArrayList<String>();
    addHttpProxyHost(proxySettings);
    addHttpProxyPort(proxySettings);
    addHttpNonProxyHosts(proxySettings);
    addHttpsProxyHost(proxySettings);
    addHttpsProxyPort(proxySettings);
    addSshProxySettings(proxySettings);
    return proxySettings;
  }

  public int getNumberOfCommitsWhenFromVersionNotFound() {
    return TeamCityProperties.getInteger("teamcity.git.from.version.not.found.commits.number", 10);
  }

  public Proxy getJschProxy() {
    String sshProxyType = TeamCityProperties.getProperty(TEAMCITY_GIT_SSH_PROXY_TYPE);
    if (isEmpty(sshProxyType))
      return null;
    String sshProxyHost = TeamCityProperties.getProperty(TEAMCITY_GIT_SSH_PROXY_HOST);
    if (isEmpty(sshProxyHost))
      return null;
    int sshProxyPort = TeamCityProperties.getInteger(TEAMCITY_GIT_SSH_PROXY_PORT, -1);
    if ("http".equals(sshProxyType)) {
      return sshProxyPort != -1 ? new ProxyHTTP(sshProxyHost, sshProxyPort) : new ProxyHTTP(sshProxyHost);
    }
    if ("socks4".equals(sshProxyType)) {
      return sshProxyPort != -1 ? new ProxySOCKS4(sshProxyHost, sshProxyPort) : new ProxySOCKS4(sshProxyHost);
    }
    if ("socks5".equals(sshProxyType)) {
      return sshProxyPort != -1 ? new ProxySOCKS5(sshProxyHost, sshProxyPort) : new ProxySOCKS5(sshProxyHost);
    }
    return null;
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
    if (!isEmpty(httpNonProxyHosts)) {
      if (SystemInfo.isUnix) {
        proxySettings.add("-Dhttp.nonProxyHosts=" + httpNonProxyHosts);
      } else {
        proxySettings.add("-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"");
      }
    }
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

  private void addSshProxySettings(List<String> proxySettings) {
    String sshProxyType = TeamCityProperties.getProperty(TEAMCITY_GIT_SSH_PROXY_TYPE);
    if (!isEmpty(sshProxyType))
      proxySettings.add("-Dteamcity.git.sshProxyType=" + sshProxyType);
    String sshProxyHost = TeamCityProperties.getProperty(TEAMCITY_GIT_SSH_PROXY_HOST);
    if (!isEmpty(sshProxyHost))
      proxySettings.add("-Dteamcity.git.sshProxyHost=" + sshProxyHost);
    int sshProxyPort = TeamCityProperties.getInteger(TEAMCITY_GIT_SSH_PROXY_PORT, -1);
    if (sshProxyPort != -1)
      proxySettings.add("-Dteamcity.git.sshProxyPort=" + sshProxyPort);
  }

  @NotNull
  public String getMonitoringDirName() {
    return "monitoring";
  }

  public int getMonitoringExpirationTimeoutHours() {
    return TeamCityProperties.getInteger("teamcity.git.monitoring.expiration.timeout.hours", 24);
  }

  public boolean alwaysCheckCiphers() {
    return TeamCityProperties.getBoolean(TEAMCITY_GIT_ALWAYS_CHECK_CIPHERS);
  }

  public boolean verboseGetContentLog() {
    return TeamCityProperties.getBoolean("teamcity.git.verbose.get.content.log");
  }

  public boolean verboseTreeWalkLog() {
    return TeamCityProperties.getBoolean("teamcity.git.verbose.tree.walk.log");
  }

  public int getMapFullPathRevisionCacheSize() {
    return TeamCityProperties.getInteger("teamcity.git.map.full.path.revision.cache.size", 100);
  }

  public long getConnectionRetryIntervalMillis() {
    return TeamCityProperties.getInteger("teamcity.git.connectionRetryIntervalSeconds", 4) * 1000L;
  }

  public int getConnectionRetryAttempts() {
    return TeamCityProperties.getInteger("teamcity.git.connectionRetryAttempts", 3);
  }

  public boolean ignoreFetchedCommits() {
    return TeamCityProperties.getBoolean("teamcity.git.mapFullPathIgnoresFetchedCommits");
  }

  @Nullable
  public CronExpression getCleanupCronExpression() {
    String cron = TeamCityProperties.getProperty("teamcity.git.cleanupCron", "0 0 2 * * ? *");
    if (isEmpty(cron))
      return null;
    try {
      return new CronExpression(cron);
    } catch (ParseException e) {
      LOG.warn("Wrong cron expression " + cron, e);
      return null;
    }
  }

  @NotNull
  public Map<String, String> getFetcherProperties() {
    Map<String, String> fetcherProps = new HashMap<String, String>();
    for (String propName : myFetcherPropertyNames) {
      fetcherProps.put(propName, TeamCityProperties.getProperty(propName));
    }
    return fetcherProps;
  }

  public boolean usePerBranchFetch() {
    return TeamCityProperties.getBoolean("teamcity.git.usePerBranchFetch");
  }

  public int getHttpsSoLinger() {
    return TeamCityProperties.getInteger("teamcity.git.httpsSoLinger", 0);
  }

  public int getListFilesTTLSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.listFilesTTLSeconds", 60);
  }

  @NotNull
  public String getHttpConnectionFactory() {
    return TeamCityProperties.getProperty("teamcity.git.httpConnectionFactory", "httpClient");
  }

  public static boolean showKnownHostsDbOption() {
    return TeamCityProperties.getBoolean("teamcity.git.showKnownHostsDbOption");
  }
}
