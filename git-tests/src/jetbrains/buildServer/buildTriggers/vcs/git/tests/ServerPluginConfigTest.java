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

import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.ProxyHTTP;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
    myTempFiles = new TempFiles();
    File dotBuildServer = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
    myPropertiesBeforeTest = copyCurrentProperties();
  }


  @AfterMethod
  public void tearDown() {
    restoreProperties();
    myTempFiles.cleanup();
  }


  private Properties copyCurrentProperties() {
    Properties result = new Properties();
    result.putAll(System.getProperties());
    return result;
  }


  private void restoreProperties() {
    Set<Object> currentKeys = new HashSet<Object>(System.getProperties().keySet());
    for (Object key : currentKeys) {
      System.clearProperty((String)key);
    }
    System.setProperties(myPropertiesBeforeTest);
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
                        "-Dhttp.nonProxyHosts=\"" + httpNonProxyHosts + "\"",
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
}
