/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.sun.net.httpserver.HttpsServer;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.GetConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SetConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl.SSLInvestigator;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.eclipse.jgit.transport.URIish;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.*;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link SSLInvestigator}.
 *
 * @author Mikhail Khorkov
 * @since 2018.1.2
 */
@Test
public class SSLInvestigatorTest {

  private TempFiles myTempFiles = new TempFiles();
  private File myHomeDirectory;
  private File myTempDirectory;
  private Mockery myMockery;
  private LoggingGitMetaFactory myLoggingFactory;

  private HttpsServer myServer;
  private SSLTestUtil mySSLTestUtil;
  private int myServerPort;

  private enum Plot {FEATURE_OFF, GOOD_CERT, BAD_CERT, NO_CERT}

  private enum Result {ONLY_GET, ONLY_SET, GET_AND_SET, GET_AND_UNSET}

  @BeforeClass
  public void init() throws Exception {
    mySSLTestUtil = new SSLTestUtil();
    myServer = mySSLTestUtil.getHttpsServer();
    myServerPort = mySSLTestUtil.getServerPort();
    myServer.start();
  }

  @AfterClass
  public void down() {
    myServer.stop(0);
  }

  @BeforeMethod
  public void setUp() throws Exception {
    myHomeDirectory = myTempFiles.createTempDir();
    myTempDirectory = myTempFiles.createTempDir();

    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
      });
    }};

    myMockery = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myLoggingFactory = new LoggingGitMetaFactory();
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  @DataProvider(name = "invariants")
  public static Object[][] invariants() {
    return new Object[][] {
      /* plot of  prerequisites | custom flag is set already | expected result */
      new Object[]{Plot.FEATURE_OFF, false, Result.ONLY_GET},
      new Object[]{Plot.FEATURE_OFF, true, Result.GET_AND_UNSET},

      new Object[]{Plot.BAD_CERT, false, Result.ONLY_GET},
      new Object[]{Plot.BAD_CERT, true, Result.GET_AND_UNSET},

      new Object[]{Plot.NO_CERT, false, Result.ONLY_GET},
      new Object[]{Plot.NO_CERT, true, Result.GET_AND_UNSET},

      new Object[]{Plot.GOOD_CERT, false, Result.ONLY_SET},
      new Object[]{Plot.GOOD_CERT, true, Result.ONLY_SET},
    };
  }

  @Test(dataProvider = "invariants")
  public void allTest(Plot plot, boolean alreadySet, Result result) throws Exception {
    switch (plot) {
      case FEATURE_OFF: {
        System.setProperty("teamcity.ssl.useCustomTrustStore.git", "false");
        break;
      }
      case NO_CERT: {
        System.setProperty("teamcity.ssl.useCustomTrustStore.git", "true");
        break;
      }
      case BAD_CERT: {
        System.setProperty("teamcity.ssl.useCustomTrustStore.git", "true");
        myTempFiles.registerAsTempFile(mySSLTestUtil.writeAnotherCert(myHomeDirectory));
        break;
      }
      case GOOD_CERT: {
        System.setProperty("teamcity.ssl.useCustomTrustStore.git", "true");
        myTempFiles.registerAsTempFile(mySSLTestUtil.writeServerCert(myHomeDirectory));
        break;
      }
    }

    final String alreadyInProperties = alreadySet ? "something" : "";
    final GitCommandProxyCallback gitCommandProxyCallback = (method, args) -> Optional.of(alreadyInProperties);
    myLoggingFactory.addCallback(GetConfigCommand.class.getName() + ".call", gitCommandProxyCallback);
    myLoggingFactory.addCallback(GetConfigCommand.class.getName() + ".callWithIgnoreExitCode", gitCommandProxyCallback);
    myLoggingFactory.addCallback(SetConfigCommand.class.getName() + ".call", (method, args) -> Optional.empty());

    final SSLInvestigator instance = createInstance();

    instance.setCertificateOptions(createFactory().create(myTempDirectory));

    switch (result) {
      case ONLY_GET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GetConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 0);
        break;
      }
      case ONLY_SET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GetConfigCommand.class), 0);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertFalse(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
      case GET_AND_SET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GetConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertFalse(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
      case GET_AND_UNSET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GetConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertTrue(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
    }
  }

  private SSLInvestigator createInstance() throws Exception {
    return new SSLInvestigator(new URIish(new URL("https://localhost:" + myServerPort)), myTempDirectory.getPath(), myHomeDirectory.getPath());
  }

  private GitFactory createFactory() throws Exception {
    final GitAgentSSHService ssh = myMockery.mock(GitAgentSSHService.class);
    final AgentPluginConfig pluginConfig = myMockery.mock(AgentPluginConfig.class);
    final Context context = myMockery.mock(Context.class);
    myMockery.checking(new Expectations() {{
      atLeast(1).of(pluginConfig).getPathToGit();
      will(returnValue("git"));
      atLeast(1).of(pluginConfig).getGitVersion();
      will(returnValue(GitVersion.MIN));
      atLeast(1).of(pluginConfig).isDeleteTempFiles();
      will(returnValue(false));
      atLeast(1).of(pluginConfig).getGitExec();
      will(returnValue(myMockery.mock(GitExec.class)));
      atLeast(1).of(pluginConfig).getCustomConfig();
      will(returnValue(Collections.emptyList()));
    }});
    final GitProgressLogger logger = myMockery.mock(GitProgressLogger.class);
    return myLoggingFactory.createFactory(ssh, pluginConfig, logger, myTempFiles.createTempDir(), Collections.emptyMap(), context);
  }
}
