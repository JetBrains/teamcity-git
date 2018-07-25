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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.GetConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SetConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl.SSLInvestigator;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl.SSLInvestigator}.
 *
 * @author Mikhail Khorkov
 * @since 2018.1.2
 */
@Test
public class SSLInvestigatorTest {

  private SSLInvestigator instance;

  private TempFiles myTempFiles = new TempFiles();
  private File myHomeDirectory;
  private File myTempDirectory;
  private SSLContext mySSLContext;
  private Mockery myMockery;
  private LoggingGitMetaFactory myLoggingFactory;

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
    mySSLContext = null;
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  @DataProvider(name = "invariants")
  public static Object[][] invariants() {
    return new Object[][] {
      /* (feature enabled | certificate exists | can connect |custom flag is set already),
       * number of sets, number of gets, is set or unset */
      new Object[] {true, true, true, true, 1, 0, true},
      new Object[] {true, true, true, false, 1, 0, true},
      new Object[] {true, true, false, true, 1, 1, false},
      new Object[] {true, true, false, false, 0, 1, false},

      new Object[] {true, false, true, true, 1, 1, false},
      new Object[] {true, false, true, false, 0, 1, false},
      new Object[] {true, false, false, true, 1, 1, false},
      new Object[] {true, false, false, false, 0, 1, false},

      new Object[] {false, true, true, true, 1, 1, false},
      new Object[] {false, true, true, false, 0, 1, false},
      new Object[] {false, true, false, true, 1, 1, false},
      new Object[] {false, true, false, false, 0, 1, false},

      new Object[] {false, false, true, true, 1, 1, false},
      new Object[] {false, false, true, false, 0, 1, false},
      new Object[] {false, false, false, true, 1, 1, false},
      new Object[] {false, false, false, false, 0, 1, false},
    };
  }

  @Test(dataProvider = "invariants")
  public void allTest(boolean featureEnables, boolean certificateExists, boolean canConnect, boolean alreadySet, int sets, int gets,
                      boolean setOrUnset) throws Exception {
    if (featureEnables) {
      System.setProperty("teamcity.ssl.useCustomTrustStore.git", "true");
    } else {
      System.setProperty("teamcity.ssl.useCustomTrustStore.git", "false");
    }

    if (certificateExists) {
      writeCert();
      mySSLContext = myMockery.mock(SSLContext.class);
    }

    final String alreadyInProperties = alreadySet ? "something" : "";
    myLoggingFactory.addCallback(GetConfigCommand.class.getName() + ".call",
                                 (method, args) -> Optional.of(alreadyInProperties));
    myLoggingFactory.addCallback(SetConfigCommand.class.getName() + ".call",
                                 (method, args) -> Optional.empty());

    instance = createInstance("https://foo.bar", mySSLContext, canConnect);
    final GitFacade gitFacade = createFactory().create(myTempDirectory);

    instance.setCertificateOptions(gitFacade);

    assertEquals(myLoggingFactory.getNumberOfCalls(SetConfigCommand.class), sets);
    if (sets > 0) {
      assertEquals(setOrUnset, !myLoggingFactory.getInvokedMethods(SetConfigCommand.class).contains("unSet"));
    }
    assertEquals(myLoggingFactory.getNumberOfCalls(GetConfigCommand.class), gets);
  }

  private SSLInvestigator createInstance(String url, SSLContext sslContext, boolean canConnect) throws Exception {
    return new SSLInvestigator(new URIish(new URL(url)), myTempDirectory.getPath(), myHomeDirectory.getPath(),
                               new SSLCheckerMock(canConnect), new SSLRetrieverMock(sslContext));
  }

  private void writeCert() throws Exception {
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(myHomeDirectory.getPath());
    final File cert = new File(certDirectory, "cert.pem");
    myTempFiles.registerAsTempFile(cert);
    cert.getParentFile().mkdirs();
    final String certContent = "-----BEGIN CERTIFICATE-----\n" +
                               "MIICQTCCAaoCCQCgsSqblM1uHDANBgkqhkiG9w0BAQUFADBlMQswCQYDVQQGEwJD\n" +
                               "TjELMAkGA1UECAwCQ1MxCzAJBgNVBAcMAkxOMQswCQYDVQQKDAJPTjEMMAoGA1UE\n" +
                               "CwwDT1VOMQswCQYDVQQDDAJDTjEUMBIGCSqGSIb3DQEJARYFYW1haWwwHhcNMTgw\n" +
                               "NzI1MDc0MTQyWhcNMTkwNzI1MDc0MTQyWjBlMQswCQYDVQQGEwJDTjELMAkGA1UE\n" +
                               "CAwCQ1MxCzAJBgNVBAcMAkxOMQswCQYDVQQKDAJPTjEMMAoGA1UECwwDT1VOMQsw\n" +
                               "CQYDVQQDDAJDTjEUMBIGCSqGSIb3DQEJARYFYW1haWwwgZ8wDQYJKoZIhvcNAQEB\n" +
                               "BQADgY0AMIGJAoGBALd6XvMOgLUrjioJxgudKQlcqbPbihcWWha1SvfY491Ya93Q\n" +
                               "q3R8AiLybJqidfdlDZFA/fiXsIs+LnQD9S+uFdC83u2gpzqlIim7A7w/X4B8JClP\n" +
                               "wNS8AebnAcn8FEgi9AOHsoBb/mitke6gUkf5TUAwsdsTDi2YV7Rdmy1Ux6GVAgMB\n" +
                               "AAEwDQYJKoZIhvcNAQEFBQADgYEAPPh1TupBgST6RVyWvJvXlcnPm3LOH3J8Jd3V\n" +
                               "+bm6+W4zs1TLjZgOzGLoTR05INISahYDjAlYZm2v0aOYm2MdxZepxSec/47K4HL2\n" +
                               "gr3hMGf7xFwTNLwxNmiTiBneuTcxfinGxAp+grq9jMaZXGWKorS1ATnyWmpXfQ8j\n" +
                               "ESLNmVw=\n" +
                               "-----END CERTIFICATE-----";
    FileUtil.writeFile(cert, certContent, "UTF-8");
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
    }});
    final GitProgressLogger logger = myMockery.mock(GitProgressLogger.class);
    return myLoggingFactory.createFactory(ssh, pluginConfig, logger, myTempFiles.createTempDir(), Collections.emptyMap(), context);
  }

  private static class SSLRetrieverMock implements SSLInvestigator.SSLContextRetriever {

    private final SSLContext mySslContext;

    private SSLRetrieverMock(@Nullable final SSLContext sslContext) {
      mySslContext = sslContext;
    }

    @Nullable
    @Override
    public SSLContext retrieve(@NotNull final String homeDirectory) {
      return mySslContext;
    }
  }

  private static class SSLCheckerMock implements SSLInvestigator.SSLChecker {

    private final boolean myCan;

    private SSLCheckerMock(final boolean can) {
      myCan = can;
    }

    @Override
    public boolean canConnect(@NotNull final SSLContext sslContext, @NotNull final String host, final int port) throws Exception {
      return myCan;
    }
  }
}
