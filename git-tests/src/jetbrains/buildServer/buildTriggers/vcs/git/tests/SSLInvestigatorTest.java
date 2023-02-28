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
import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl.SSLInvestigator;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.SetConfigCommand;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.eclipse.jgit.transport.URIish;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.*;

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
    myLoggingFactory.addCallback(GitConfigCommand.class.getName() + ".call", gitCommandProxyCallback);
    myLoggingFactory.addCallback(GitConfigCommand.class.getName() + ".callWithIgnoreExitCode", gitCommandProxyCallback);
    myLoggingFactory.addCallback(SetConfigCommand.class.getName() + ".call", (method, args) -> Optional.empty());

    final SSLInvestigator instance = createInstance();

    instance.setCertificateOptions(createFactory().create(myTempDirectory));

    switch (result) {
      case ONLY_GET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GitConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 0);
        break;
      }
      case ONLY_SET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GitConfigCommand.class), 0);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertFalse(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
      case GET_AND_SET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GitConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertFalse(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
      case GET_AND_UNSET: {
        assertEquals(myLoggingFactory.getNumberOfCalls(GitConfigCommand.class), 1);
        assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), 1);
        assertTrue(myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
        break;
      }
    }
  }

  private SSLInvestigator createInstance() throws Exception {
    BuildAgentConfiguration buildAgentConfiguration = myMockery.mock(BuildAgentConfiguration.class);
    File systemDir = new File(myHomeDirectory, "system");
    myMockery.checking(new Expectations() {{
      allowing(buildAgentConfiguration).getCacheDirectory(with(TrustedCertificatesDirectory.SERVER_CERTIFICATES_DIRECTORY));
      will(returnValue(new File(systemDir, TrustedCertificatesDirectory.SERVER_CERTIFICATES_DIRECTORY)));
      allowing(buildAgentConfiguration).getCacheDirectory(with(TrustedCertificatesDirectory.ALL_CERTIFICATES_DIRECTORY));
      will(returnValue(new File(systemDir, TrustedCertificatesDirectory.ALL_CERTIFICATES_DIRECTORY)));
    }});

    return new SSLInvestigator(new URIish(new URL("https://localhost:" + myServerPort)), myTempDirectory.getPath(), buildAgentConfiguration);
  }

  private GitFactory createFactory() {
    final GitAgentSSHService ssh = myMockery.mock(GitAgentSSHService.class);
    final GitProgressLogger logger = myMockery.mock(GitProgressLogger.class);
    final Context context = myMockery.mock(Context.class);
    myMockery.checking(new Expectations() {{
      atLeast(1).of(context).getGitVersion();
      will(returnValue(GitVersion.MIN));
      atLeast(1).of(context).isDeleteTempFiles();
      will(returnValue(false));
      atLeast(1).of(context).getGitExec();
      will(returnValue(new GitExec("git", GitVersion.MIN)));
      atLeast(1).of(context).getCustomConfig();
      will(returnValue(Collections.emptyList()));
      atLeast(1).of(context).getLogger();
      will(returnValue(logger));
      atLeast(1).of(context).getTempDir();
      will(returnValue(myTempDirectory));
      atLeast(1).of(context).getEnv();
      will(returnValue(Collections.emptyMap()));
    }});
    return myLoggingFactory.createFactory(ssh, context);
  }
}
