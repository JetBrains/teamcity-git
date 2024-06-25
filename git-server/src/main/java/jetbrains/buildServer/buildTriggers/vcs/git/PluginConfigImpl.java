

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.*;
import com.jcraft.jzlib.JZlib;
import gnu.trove.TObjectHashingStrategy;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.jsch.SshPubkeyAcceptedAlgorithms;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchProcess;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.metrics.Counter;
import jetbrains.buildServer.connections.ExpiringAccessToken;
import jetbrains.buildServer.serverSide.CachePaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.impl.ssh.ServerSshKnownHostsManagerImpl;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.LowLevelPatchBuilder;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import org.apache.commons.codec.Decoder;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.log4j.BasicConfigurator;
import org.bouncycastle.asn1.smime.SMIMEAttributes;
import org.bouncycastle.crypto.CipherParameters;
import org.eclipse.jgit.lfs.LfsBlobLoader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.jdom2.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static java.util.Arrays.asList;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;

/**
 * @author dmitry.neverov
 */
public class PluginConfigImpl implements ServerPluginConfig {

  public static final String TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS = "teamcity.git.idle.timeout.seconds";
  public static final String MAP_FULL_PATH_PERSISTENT_CACHES = "teamcity.git.persistentCacheEnabled";
  private static final String TEAMCITY_GIT_SSH_PROXY_TYPE = "teamcity.git.sshProxyType";
  private static final String TEAMCITY_GIT_SSH_PROXY_HOST = "teamcity.git.sshProxyHost";
  private static final String TEAMCITY_GIT_SSH_PROXY_PORT = "teamcity.git.sshProxyPort";
  private static final String TEAMCITY_GIT_ALWAYS_CHECK_CIPHERS = "teamcity.git.always.check.ciphers";
  private static final String HTTP_CONNECTION_FACTORY = "teamcity.git.httpConnectionFactory";
  private static final String HTTP_CONNECTION_SSL_PROTOCOL = "teamcity.git.httpConnectionSslProtocol";
  private static final String MONITORING_FILE_THRESHOLD_SECONDS = "teamcity.git.monitoringFileThresholdSeconds";
  public static final String IGNORE_MISSING_REMOTE_REF = "teamcity.git.ignoreMissingRemoteRef";
  private static final String ACCESS_TIME_UPDATE_RATE_MINUTES = "teamcity.git.accessTimeUpdateRateMinutes";
  private static final String MERGE_RETRY_ATTEMPTS = "teamcity.git.mergeRetryAttemps";
  private static final String GET_REPOSITORY_STATE_TIMEOUT_SECONDS = "teamcity.git.repositoryStateTimeoutSeconds";
  public static final String TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY = "teamcity.git.fetch.process.max.memory";
  public static final String TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY_LIMIT = "teamcity.git.fetch.process.max.memory.limit";
  public static final String CONNECTION_RETRY_INTERVAL_SECONDS = "teamcity.git.connectionRetryIntervalSeconds";
  public static final String CONNECTION_RETRY_ATTEMPTS = "teamcity.git.connectionRetryAttempts";
  public static final String GIT_TRACE_ENV = "teamcity.git.traceEnv";
  private static final String USE_DEFAULT_CHARSET = "teamcity.git.useDefaultCharset";
  private static final String GIT_OUTPUT_CHARSET = "teamcity.git.outputCharset";
  private static final String PATCH_DOWNLOAD_LFS_OBJECTS = "teamcity.git.patch.downloadLfsObjects";
  private static final String FETCH_DURATION_METRIC_REPOS = "teamcity.git.fetch.durationMetricRepos";

  private final static Logger LOG = Logger.getInstance(PluginConfigImpl.class.getName());
  private final static int GB = 1024 * 1024 * 1024;//bytes

  public static final float FETCH_PROCESS_MAX_MEMORY_MULT_FACTOR_DEFAULT = 1.4f;
  public static final String FETCH_REMOTE_BRANCHES_FACTOR = "teamcity.server.git.fetchRemoteBranchesFactor";
  public static final String FETCH_REMOTE_BRANCHES_THRESHOLD = "teamcity.server.git.fetchRemoteBranchesThreshold";
  public static final int FETCH_REMOTE_BRANCHES_THRESHOLD_DEFAULT = 200;
  private static final String GIT_PRUNE_TIMEOUT_SECONDS = "teamcity.git.pruneTimeoutSeconds";

  private final File myCachesDir;

  private final File mySslDir;
  private final Set<String> myFetcherPropertyNames = setOf(TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS,
                                                           TEAMCITY_GIT_SSH_PROXY_TYPE,
                                                           TEAMCITY_GIT_SSH_PROXY_HOST,
                                                           TEAMCITY_GIT_SSH_PROXY_PORT,
                                                           TEAMCITY_GIT_ALWAYS_CHECK_CIPHERS,
                                                           HTTP_CONNECTION_FACTORY,
                                                           HTTP_CONNECTION_SSL_PROTOCOL,
                                                           Constants.AMAZON_HOSTS,
                                                           MONITORING_FILE_THRESHOLD_SECONDS,
                                                           GET_REPOSITORY_STATE_TIMEOUT_SECONDS,
                                                           IGNORE_MISSING_REMOTE_REF,
                                                           CONNECTION_RETRY_INTERVAL_SECONDS,
                                                           CONNECTION_RETRY_ATTEMPTS,
                                                           JSchConfigInitializer.JSCH_CONFIG_INT_PROPERTY_PREFIX,
                                                           SshPubkeyAcceptedAlgorithms.DOMAINS_WITH_ENFORCED_SHA_1_SIGNATURE);

  public PluginConfigImpl() {
    myCachesDir = null;
    mySslDir = null;
  }

  public PluginConfigImpl(@NotNull final CachePaths paths) {
    myCachesDir = paths.getCacheDirectory("git");
    mySslDir = paths.getCacheDirectory("ssl");
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

  @NotNull
  @Override
  public File getSslDir() {
    if (mySslDir == null)
      throw new IllegalStateException("Ssl dir is not initialized");
    return mySslDir;
  }

  public int getStreamFileThresholdMb() {
    int defaultThreshold = 128;
    int threshold = TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", defaultThreshold);
    if (threshold <= 0)
      return 128;
    return threshold;
  }


  public String getFetchProcessJavaPath() {
    final String jdkHome = System.getProperty("java.home");
    File defaultJavaExec = new File(jdkHome.replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "java");
    return TeamCityProperties.getProperty("teamcity.git.fetch.process.java.exec", defaultJavaExec.getAbsolutePath());
  }


  @NotNull
  public String getFetchProcessMaxMemory() {
    String maxMemory = getExplicitFetchProcessMaxMemory();
    if (!isEmpty(maxMemory))
      return maxMemory;
    try {
      Class.forName("com.sun.management.OperatingSystemMXBean");
    } catch (ClassNotFoundException e) {
      return "512M";
    }
    final Long freeRAM = GitServerUtil.getFreePhysicalMemorySize();
    if (freeRAM != null && freeRAM > GB) {
       return "1024M";
    } else {
      return "512M";
    }
  }


  public String getGcProcessMaxMemory() {
    String xmx = TeamCityProperties.getProperty("teamcity.git.gcXmx");
    if (!isEmpty(xmx))
      return xmx;
    try {
      Class.forName("com.sun.management.OperatingSystemMXBean");
    } catch (ClassNotFoundException e) {
      return "768M";
    }
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
      long freeRAM = ((com.sun.management.OperatingSystemMXBean) osBean).getFreePhysicalMemorySize();
      if (freeRAM > GB)
        return "1024M";
    }
    return "768M";
  }

  @Nullable
  public String getExplicitFetchProcessMaxMemory() {
    return TeamCityProperties.getPropertyOrNull(TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY);
  }

  @Nullable
  public String getMaximumFetchProcessMaxMemory() {
    return TeamCityProperties.getPropertyOrNull(TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY_LIMIT);
  }

  public boolean isSeparateProcessForFetch() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.fetch.separate.process");
  }


  public boolean isSeparateProcessForPatch() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.buildPatchInSeparateProcess");
  }

  public boolean isRunNativeGC() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.server.git.gc.enabled");
  }

  public boolean isRunJGitGC() {
    return TeamCityProperties.getBoolean("teamcity.git.jgitGcEnabled");
  }

  public String getPathToGit() {
    return TeamCityProperties.getProperty("teamcity.server.git.executable.path", "git");
  }

  public int getNativeGCQuotaMinutes() {
    return TeamCityProperties.getInteger("teamcity.server.git.gc.quota.minutes", 300);
  }

  public String getFetchClasspath() {
    Set<Class> classes = fetchProcessClasses();
    return ClasspathUtil.composeClasspath(classes.toArray(new Class[classes.size()]), null, null);
  }


  public String getFetcherClassName() {
    return Fetcher.class.getName();
  }

  public String getPatchClasspath() {
    Set<Class> classes = fetchProcessClasses();
    classes.add(AbstractPatchBuilder.class);
    classes.add(PatchBuilderImpl.class);
    classes.add(LowLevelPatchBuilder.class);
    classes.add(org.slf4j.Logger.class);
    classes.add(org.slf4j.impl.StaticLoggerBinder.class);
    classes.add(EWAHCompressedBitmap.class);
    classes.add(LfsBlobLoader.class);
    classes.add(Counter.class);
    return ClasspathUtil.composeClasspath(classes.toArray(new Class[classes.size()]), null, null);
  }

  public String getPatchBuilderClassName() {
    return GitPatchProcess.class.getName();
  }

  public boolean passEnvToChildProcess() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.passEnvToChildProcess");
  }

  private Set<Class> fetchProcessClasses() {
    Set<Class> result = new HashSet<>();
    result.addAll(asList(
      Fetcher.class,
      VcsRoot.class,
      VcsRootInstance.class,
      SVcsRoot.class,
      LVcsRootInstance.class,
      LVcsRoot.class,
      ProgressMonitor.class,
      VcsPersonalSupport.class,
      Logger.class,
      GitVcsRoot.class,
      JSch.class,
      CipherParameters.class,
      SMIMEAttributes.class,
      JZlib.class,
      Decoder.class,
      TObjectHashingStrategy.class,
      EncryptUtil.class,
      DiagnosticUtil.class,
      FileUtil.class,
      Element.class,
      org.apache.log4j.Logger.class,
      org.apache.logging.log4j.core.Logger.class,
      org.apache.logging.log4j.Logger.class,
      VcsException.class,
      VcsOperationRejectedException.class,
      BasicConfigurator.class,
      HttpClientConnectionFactory.class,
      HttpClient.class,
      LogFactory.class,
      HttpEntity.class,
      CachePaths.class,
      ServiceMessage.class,
      org.slf4j.Logger.class,
      org.slf4j.impl.StaticLoggerBinder.class,
      EWAHCompressedBitmap.class,
      JschConfigSessionFactory.class,
      JSchConfigInitializer.class,
      Pair.class,
      ExpiringAccessToken.class,
      ServerSshKnownHostsManagerImpl.class
    ));
    Collections.addAll(result, GitVcsSupport.class.getInterfaces());
    return result;
  }

  public int getFixedSubmoduleCommitSearchDepth() {
    return TeamCityProperties.getInteger("teamcity.server.git.fixed.submodule.commit.search.depth", 100);
  }

  public int getIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger(TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS, DEFAULT_IDLE_TIMEOUT);
  }

  public int getFetchTimeoutSeconds() {
    int deprecatedFetchTimeout = TeamCityProperties.getInteger("teamcity.git.fetch.timeout", DEFAULT_IDLE_TIMEOUT);
    int idleTimeout = getIdleTimeoutSeconds();
    if (deprecatedFetchTimeout > idleTimeout)
      return deprecatedFetchTimeout;
    return idleTimeout;
  }

  @Override
  public int getPushTimeoutSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.pushIdleTimeoutSeconds", getIdleTimeoutSeconds());
  }

  public int getRepositoryStateTimeoutSeconds() {
    return TeamCityProperties.getInteger(GET_REPOSITORY_STATE_TIMEOUT_SECONDS, 60);
  }

  public int getPatchProcessIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.patchProcessIdleTimeoutSeconds", 1800);
  }

  @Override
  public int getPruneTimeoutSeconds() {
    return TeamCityProperties.getInteger(GIT_PRUNE_TIMEOUT_SECONDS, 3600);
  }

  public long getMirrorExpirationTimeoutMillis() {
    int days = TeamCityProperties.getInteger("teamcity.git.mirror.expiration.timeout.days", 7);
    return days * Dates.ONE_DAY;
  }

  private void addProxySettingsForSeparateProcess(@NotNull List<String> options) {
    addHttpProxyHost(options);
    addHttpProxyPort(options);
    addHttpNonProxyHosts(options);
    addHttpsProxyHost(options);
    addHttpsProxyPort(options);
    addSshProxySettings(options);
  }

  @NotNull
  public List<String> getOptionsForSeparateProcess() {
    List<String> options = new ArrayList<>();
    addProxySettingsForSeparateProcess(options);
    addSslTrustStoreSettingsForSeparateProcess(options);
    addInheritedOption(options, "java.net.preferIPv6Addresses");
    addInheritedOption(options, "jsse.enableSNIExtension");
    String additionalCommandLineOpts = TeamCityProperties.getProperty("teamcity.git.separateProcess.additionalArguments");
    if (!StringUtil.isEmpty(additionalCommandLineOpts)) {
      options.addAll(StringUtil.splitCommandArgumentsAndUnquote(additionalCommandLineOpts));
    }
    return options;
  }

  private void addSslTrustStoreSettingsForSeparateProcess(@NotNull List<String> options) {
    addInheritedOption(options, "javax.net.ssl.trustStore");
    addInheritedOption(options, "javax.net.ssl.trustStorePassword");
  }

  private void addInheritedOption(@NotNull List<String> options, @NotNull String key) {
    String value = System.getProperty(key);
    if (!isEmpty(value))
      options.add("-D" + key + "=" + value);
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
    String httpProxyHost = getFirstNotNullProperty("teamcity.http.proxyHost", "http.proxyHost");
    if (!isEmpty(httpProxyHost))
      proxySettings.add("-Dhttp.proxyHost=" + httpProxyHost);
  }

  private void addHttpProxyPort(List<String> proxySettings) {
    int httpProxyPort = getFirstNotNullIntegerProperty("teamcity.http.proxyPort", "http.proxyPort");
    if (httpProxyPort != -1)
      proxySettings.add("-Dhttp.proxyPort=" + httpProxyPort);
  }

  private void addHttpNonProxyHosts(List<String> proxySettings) {
    String httpNonProxyHosts = getFirstNotNullProperty("teamcity.http.nonProxyHosts", "http.nonProxyHosts");
    if (!isEmpty(httpNonProxyHosts)) {
      if (!SystemInfo.isUnix) {
        httpNonProxyHosts = "\"" + httpNonProxyHosts + "\"";
      }
      proxySettings.add("-Dhttp.nonProxyHosts=" + httpNonProxyHosts);
    }
  }

  private void addHttpsProxyHost(List<String> proxySettings) {
    String httpsProxyHost = getFirstNotNullProperty("teamcity.https.proxyHost", "https.proxyHost");
    if (!isEmpty(httpsProxyHost))
      proxySettings.add("-Dhttps.proxyHost=" + httpsProxyHost);
  }

  private void addHttpsProxyPort(List<String> proxySettings) {
    int httpsProxyPort = getFirstNotNullIntegerProperty("teamcity.https.proxyPort", "https.proxyPort");
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

  @Nullable
  private String getFirstNotNullProperty(String ... keys) {
    for (String key : keys) {
      final String value = TeamCityProperties.getProperty(key);
      if (!isEmpty(value)) {
        return value;
      }
    }
    return null;
  }

  private int getFirstNotNullIntegerProperty(String ... keys) {
    for (String key : keys) {
      final int value = TeamCityProperties.getInteger(key);
      if (value != 0) {
        return value;
      }
    }
    return -1;
  }

  @NotNull
  public String getMonitoringDirName() {
    return "monitoring";
  }

  public int getMonitoringExpirationTimeoutHours() {
    return TeamCityProperties.getInteger("teamcity.git.monitoring.expiration.timeout.hours", 24);
  }

  @Override
  public long getMonitoringFileThresholdMillis() {
    int thresholdSeconds = TeamCityProperties.getInteger(MONITORING_FILE_THRESHOLD_SECONDS, 60 * 10 /*10 minutes*/);
    return thresholdSeconds * 1000L;
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
    return TeamCityProperties.getInteger(CONNECTION_RETRY_INTERVAL_SECONDS, 4) * 1000L;
  }

  public int getConnectionRetryAttempts() {
    return TeamCityProperties.getInteger(CONNECTION_RETRY_ATTEMPTS, 1);
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
      TeamCityProperties.getPropertiesWithPrefix(propName).forEach((k, v) -> {
        fetcherProps.put(k, v);
      });
    }
    return fetcherProps;
  }

  public int getHttpsSoLinger() {
    return TeamCityProperties.getInteger("teamcity.git.httpsSoLinger", 0);
  }

  public int getListFilesTTLSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.listFilesTTLSeconds", 60);
  }

  @NotNull
  public String getHttpConnectionFactory() {
    return TeamCityProperties.getProperty(HTTP_CONNECTION_FACTORY, "httpClient");
  }

  @NotNull
  public String getHttpConnectionSslProtocol() {
    return TeamCityProperties.getProperty(HTTP_CONNECTION_SSL_PROTOCOL, "SSL");
  }

  public static boolean showKnownHostsDbOption() {
    return TeamCityProperties.getBoolean("teamcity.git.showKnownHostsDbOption");
  }

  @NotNull
  public List<String> getAmazonHosts() {
    String amazonHosts = TeamCityProperties.getProperty(Constants.AMAZON_HOSTS);
    if (isEmptyOrSpaces(amazonHosts))
      return Collections.emptyList();
    String[] hosts = amazonHosts.split(",");
    return asList(hosts);
  }

  public boolean useTagPackHeuristics() {
    return TeamCityProperties.getBoolean("teamcity.git.useTagPackHeuristics");
  }

  public boolean analyzeTagsInPackHeuristics() {
    return TeamCityProperties.getBoolean("teamcity.git.tagPackHeuristicsAnalyzeTags");
  }

  public boolean checkLabeledCommitIsInRemoteRepository() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.tagPackHeuristicsCheckCommit");
  }

  @Override
  public boolean failLabelingWhenPackHeuristicsFails() {
    return TeamCityProperties.getBoolean("teamcity.git.failLabelingWhenPackHeuristicsFails");
  }

  @Override
  public boolean persistentCacheEnabled() {
    return TeamCityProperties.getBooleanOrTrue(MAP_FULL_PATH_PERSISTENT_CACHES);
  }

  @Override
  public boolean logRemoteRefs() {
    return TeamCityProperties.getBoolean("teamcity.git.logRemoteRefs");
  }

  @Override
  public long getAccessTimeUpdateRateMinutes() {
    return TeamCityProperties.getLong(ACCESS_TIME_UPDATE_RATE_MINUTES, 5);
  }

  public boolean ignoreMissingRemoteRef() {
    return TeamCityProperties.getBoolean(IGNORE_MISSING_REMOTE_REF);
  }

  @Override
  public int getMergeRetryAttempts() {
    return TeamCityProperties.getInteger(MERGE_RETRY_ATTEMPTS, 2);
  }

  @Override
  public boolean runInPlaceGc() {
    return TeamCityProperties.getBoolean("teamcity.git.runInPlaceGc");
  }

  @Override
  public int getRepackIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.repackIdleTimeoutSeconds", (int) TimeUnit.MINUTES.toSeconds(30));
  }

  @Override
  @NotNull
  public List<String> getRepackCommandArguments() {
    return StringUtil.splitCommandArgumentsAndUnquote(TeamCityProperties.getProperty("teamcity.git.repack.args", "-a -d --max-pack-size=400m"));
  }

  @Override
  public int getPackRefsIdleTimeoutSeconds() {
    return TeamCityProperties.getInteger("teamcity.git.packRefsIdleTimeoutSeconds", (int) TimeUnit.MINUTES.toSeconds(5));
  }

  @Override
  public boolean treatMissingBranchTipAsRecoverableError() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.treatMissingCommitAsRecoverableError");
  }

  @Override
  public boolean reportPerParentChangedFiles() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.reportPerParentChangedFiles");
  }

  @Override
  public boolean shouldSetSubmoduleUserInAbsoluteUrls() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.setSubmoduleUserInAbsoluteUrls");
  }

  @NotNull
  @Override
  public List<String> getRecoverableFetchErrorMessages() {
    String errorsStr = TeamCityProperties.getProperty("teamcity.git.recoverableFetchErrors");
    if (isEmptyOrSpaces(errorsStr))
      return Collections.emptyList();
    String[] errors = errorsStr.split(",");
    return asList(errors);
  }

  @Override
  public float getFetchProcessMemoryMultiplyFactor() {
    return TeamCityProperties.getFloat("teamcity.git.fetch.process.max.memory.multiply.factor", FETCH_PROCESS_MAX_MEMORY_MULT_FACTOR_DEFAULT);
  }

  @Override
  public boolean fetchAllRefsEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.fetch.all.refs.enabled");
  }

  @Override
  public long repositoryWriteLockTimeout() {
    return TeamCityProperties.getLong("teamcity.git.repositoryWriteLockTimeoutSeconds", 60);
  }

  @Override
  public boolean refreshObjectDatabaseAfterFetch() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.server.git.fetch.refreshObjectDatabase");
  }

  @Override
  public float fetchRemoteBranchesFactor() {
    final float factor = TeamCityProperties.getFloat(FETCH_REMOTE_BRANCHES_FACTOR, 0);
    if (factor > 1) {
      LOG.warn(String.format("Unexpected \"%s\" value \"%s\": the value should be a float number from 0 to 1, where 0 means the feature is disabled", FETCH_REMOTE_BRANCHES_FACTOR, factor));
    }
    return factor;
  }

  @Override
  public int fetchRemoteBranchesThreshold() {
    final int threshold = TeamCityProperties.getInteger(FETCH_REMOTE_BRANCHES_THRESHOLD, FETCH_REMOTE_BRANCHES_THRESHOLD_DEFAULT);
    if (threshold <= 0) {
      LOG.warn(String.format("Unexpected \"%s\" value \"%s\": the value should be a positive integer number", FETCH_REMOTE_BRANCHES_THRESHOLD, threshold));
    }
    return threshold;
  }

  @NotNull
  @Override
  public Map<String, String> getGitTraceEnv() {
    final String prop = TeamCityProperties.getProperty(GIT_TRACE_ENV);
    if (StringUtil.isEmpty(prop)) return Collections.emptyMap();
    try {
      return PropertiesUtil.toMap(PropertiesUtil.loadProperties(new ByteArrayInputStream(prop.replace(' ', '\n').getBytes(StandardCharsets.UTF_8))));
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Failed to parse \"" + GIT_TRACE_ENV + "\" property value \"" + prop + "\", git trace won't be enabled", e);
      return Collections.emptyMap();
    }
  }

  @Nullable
  @Override
  public String getGitOutputCharsetName() {
    final boolean useDefault = TeamCityProperties.getBoolean(USE_DEFAULT_CHARSET);
    if (useDefault) return null;

    final String charsetName = TeamCityProperties.getProperty(GIT_OUTPUT_CHARSET);
    return StringUtil.isNotEmpty(charsetName) ? charsetName : "UTF-8";
  }

  @Override
  public boolean downloadLfsObjectsForPatch() {
    return TeamCityProperties.getBoolean(PATCH_DOWNLOAD_LFS_OBJECTS);
  }

  @NotNull
  @Override
  public List<String> getFetchDurationMetricRepos() {
    final String prop = TeamCityProperties.getPropertyOrNull(FETCH_DURATION_METRIC_REPOS);
    return prop == null ? Collections.emptyList() : Arrays.asList(prop.replace("\r\n", ";").replace("\n", ";").split(";"));

  }
}