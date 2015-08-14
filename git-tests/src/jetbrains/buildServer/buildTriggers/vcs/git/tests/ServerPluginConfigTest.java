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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.ProxyHTTP;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
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
public class ServerPluginConfigTest {

  private TempFiles myTempFiles;
  private ServerPaths myServerPaths;
  private Properties myPropertiesBeforeTest;


  @BeforeMethod
  public void setUp() throws Exception {
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};

    myTempFiles = new TempFiles();
    File dotBuildServer = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
    myPropertiesBeforeTest = GitTestUtil.copyCurrentProperties();
  }


  @AfterMethod
  public void tearDown() {
    GitTestUtil.restoreProperties(myPropertiesBeforeTest);
    myTempFiles.cleanup();
  }


  public void test_default_idle_timeout() {
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void test_idle_timeout() {
    System.setProperty("teamcity.git.idle.timeout.seconds", "60");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(60, config.getIdleTimeoutSeconds());
  }


  public void should_correct_negative_stream_threshold() {
    System.setProperty("teamcity.git.stream.file.threshold.mb", "-1");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertTrue(config.getStreamFileThresholdMb() > 0);
  }


  public void should_correct_zero_stream_threshold() {
    System.setProperty("teamcity.git.stream.file.threshold.mb", "0");
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertTrue(config.getStreamFileThresholdMb() > 0);
  }


  public void test_proxy_settings() {
    final String httpProxyHost = "some.org";
    final String httpProxyPort = "3128";
    final String httpNonProxyHosts = "localhost|*.mydomain.com";
    final String httpsProxyPort = "3129";
    final String httpsProxyHost = "other.org";
    System.setProperty("http.proxyHost", httpProxyHost);
    System.setProperty("http.proxyPort", httpProxyPort);
    System.setProperty("http.nonProxyHosts", httpNonProxyHosts);
    System.setProperty("https.proxyHost", httpsProxyHost);
    System.setProperty("https.proxyPort", httpsProxyPort);

    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    assertEquals(asList("-Dhttp.proxyHost=" + httpProxyHost,
                        "-Dhttp.proxyPort=" + httpProxyPort,
                        SystemInfo.isUnix ? "-Dhttp.nonProxyHosts=" + httpNonProxyHosts
                                          : "-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"",
                        "-Dhttps.proxyHost=" + httpsProxyHost,
                        "-Dhttps.proxyPort=" + httpsProxyPort),
                 config.getProxySettingsForSeparateProcess());
    assertNull(config.getJschProxy());
  }

  @TestFor(issues = "TW-26507")
  public void ssh_proxy_settings() {
    final String sshProxyHost = "acme.org";
    final String sshProxyPort = "3128";
    final String sshProxyType = "http";
    System.setProperty("teamcity.git.sshProxyType", sshProxyType);
    System.setProperty("teamcity.git.sshProxyHost", sshProxyHost);
    System.setProperty("teamcity.git.sshProxyPort", sshProxyPort);

    ServerPluginConfig config = new PluginConfigImpl();
    Proxy sshProxy = config.getJschProxy();
    assertNotNull(sshProxy);
    assertTrue(sshProxy instanceof ProxyHTTP);

    List<String> separateProcessProxySettings = config.getProxySettingsForSeparateProcess();
    assertThat(separateProcessProxySettings, hasItem("-Dteamcity.git.sshProxyType=" + sshProxyType));
    assertThat(separateProcessProxySettings, hasItem("-Dteamcity.git.sshProxyHost=" + sshProxyHost));
    assertThat(separateProcessProxySettings, hasItem("-Dteamcity.git.sshProxyPort=" + sshProxyPort));
  }


  public void amazon_hosts() {
    ServerPluginConfig config = new PluginConfigImpl();
    assertTrue(config.getAmazonHosts().isEmpty());
    System.setProperty(Constants.AMAZON_HOSTS, "host1");
    assertThat(config.getAmazonHosts(), hasItems("host1"));
    System.setProperty(Constants.AMAZON_HOSTS, "host1,host2");
    assertThat(config.getAmazonHosts(), hasItems("host1", "host2"));
  }
}
