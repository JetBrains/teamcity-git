

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.ProxyHTTP;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseSimpleGitTestCase;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class ServerPluginConfigTest extends BaseSimpleGitTestCase {

  private TempFiles myTempFiles;
  private ServerPaths myServerPaths;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};

    myTempFiles = new TempFiles();
    File dotBuildServer = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
  }


  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    myTempFiles.cleanup();
  }


  public void test_default_idle_timeout() {
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void test_idle_timeout() {
    setInternalProperty("teamcity.git.idle.timeout.seconds", "60");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(60, config.getIdleTimeoutSeconds());
  }


  public void should_correct_negative_stream_threshold() {
    setInternalProperty("teamcity.git.stream.file.threshold.mb", "-1");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertTrue(config.getStreamFileThresholdMb() > 0);
  }


  public void should_correct_zero_stream_threshold() {
    setInternalProperty("teamcity.git.stream.file.threshold.mb", "0");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertTrue(config.getStreamFileThresholdMb() > 0);
  }


  public void test_proxy_settings() {
    final String httpProxyHost = "some.org";
    final String httpProxyPort = "3128";
    final String httpNonProxyHosts = "localhost|*.mydomain.com";
    final String httpsProxyPort = "3129";
    final String httpsProxyHost = "other.org";
    setInternalProperty("http.proxyHost", httpProxyHost);
    setInternalProperty("http.proxyPort", httpProxyPort);
    setInternalProperty("http.nonProxyHosts", httpNonProxyHosts);
    setInternalProperty("https.proxyHost", httpsProxyHost);
    setInternalProperty("https.proxyPort", httpsProxyPort);

    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(asList("-Dhttp.proxyHost=" + httpProxyHost,
                        "-Dhttp.proxyPort=" + httpProxyPort,
                        SystemInfo.isUnix ? "-Dhttp.nonProxyHosts=" + httpNonProxyHosts
                                          : "-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"",
                        "-Dhttps.proxyHost=" + httpsProxyHost,
                        "-Dhttps.proxyPort=" + httpsProxyPort),
                 config.getOptionsForSeparateProcess());
    assertNull(config.getJschProxy());
  }

  @TestFor(issues = "TW-57178")
  public void test_modern_proxy_settings() {
    final String httpProxyHost = "some.org";
    final String httpProxyPort = "3128";
    final String httpNonProxyHosts = "localhost|*.mydomain.com";
    final String httpsProxyPort = "3129";
    final String httpsProxyHost = "other.org";
    setInternalProperty("teamcity.http.proxyHost", httpProxyHost);
    setInternalProperty("teamcity.http.proxyPort", httpProxyPort);
    setInternalProperty("teamcity.http.nonProxyHosts", httpNonProxyHosts);
    setInternalProperty("teamcity.https.proxyHost", httpsProxyHost);
    setInternalProperty("teamcity.https.proxyPort", httpsProxyPort);

    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(asList("-Dhttp.proxyHost=" + httpProxyHost,
                        "-Dhttp.proxyPort=" + httpProxyPort,
                        SystemInfo.isUnix ? "-Dhttp.nonProxyHosts=" + httpNonProxyHosts
                                          : "-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"",
                        "-Dhttps.proxyHost=" + httpsProxyHost,
                        "-Dhttps.proxyPort=" + httpsProxyPort),
                 config.getOptionsForSeparateProcess());
    assertNull(config.getJschProxy());
  }

  @TestFor(issues = "TW-26507")
  public void ssh_proxy_settings() {
    final String sshProxyHost = "acme.org";
    final String sshProxyPort = "3128";
    final String sshProxyType = "http";
    setInternalProperty("teamcity.git.sshProxyType", sshProxyType);
    setInternalProperty("teamcity.git.sshProxyHost", sshProxyHost);
    setInternalProperty("teamcity.git.sshProxyPort", sshProxyPort);

    ServerPluginConfig config = new PluginConfigImpl();
    Proxy sshProxy = config.getJschProxy();
    assertNotNull(sshProxy);
    assertTrue(sshProxy instanceof ProxyHTTP);

    List<String> separateProcessOptions = config.getOptionsForSeparateProcess();
    assertThat(separateProcessOptions, hasItem("-Dteamcity.git.sshProxyType=" + sshProxyType));
    assertThat(separateProcessOptions, hasItem("-Dteamcity.git.sshProxyHost=" + sshProxyHost));
    assertThat(separateProcessOptions, hasItem("-Dteamcity.git.sshProxyPort=" + sshProxyPort));
  }


  public void amazon_hosts() {
    ServerPluginConfig config = new PluginConfigImpl();
    assertTrue(config.getAmazonHosts().isEmpty());
    setInternalProperty(Constants.AMAZON_HOSTS, "host1");
    assertThat(config.getAmazonHosts(), hasItems("host1"));
    setInternalProperty(Constants.AMAZON_HOSTS, "host1,host2");
    assertThat(config.getAmazonHosts(), hasItems("host1", "host2"));
  }
}