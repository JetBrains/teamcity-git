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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl.USE_ALTERNATES;
import static jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl.USE_MIRRORS;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitVersionProvider.getGitPath;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class AgentSslCheckoutTest extends BaseRemoteRepositoryTest {

  private GitAgentVcsSupport myVcsSupport;
  private File myCheckoutDir;
  private File myHomeDirectory;
  private VcsRoot myRoot;
  private AgentSupportBuilder myAgentSupportBuilder;

  public AgentSslCheckoutTest() {
    super();
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myHomeDirectory = myAgentConfiguration.getTempDirectory();
    myCheckoutDir = myTempFiles.createTempDir();
    myAgentSupportBuilder = new AgentSupportBuilder(myTempFiles);
    myVcsSupport = myAgentSupportBuilder.build();
    String pathToGit = getGitPath();
    myRoot = vcsRoot()
      .withAgentGitPath(pathToGit)
      .withFetchUrl("https://github.com/JetBrains/teamcity-commit-hooks.git")
      .withBranch("master")
      .build();
  }

  @DataProvider(name = "github-data")
  public static Object[][] invariants() {
    return new Object[][]{
      /* (write cert | use mirrors | use alternative) */
      new Object[]{false, false, false},
      new Object[]{false, true, false},
      new Object[]{false, false, true},

      new Object[]{true, false, false},
      new Object[]{true, true, false},
      new Object[]{true, false, false},
    };
  }

  @Test(dataProvider = "github-data")
  public void githubTest(boolean writeCert, boolean useMirrors, boolean useAlternative) throws Exception {
    if (writeCert) {
      writeCertificate();
    }
    final AgentRunningBuildBuilder runningBuild = runningBuild().addRoot(myRoot).withCheckoutDir(myCheckoutDir);
    if (useMirrors) {
      runningBuild.sharedConfigParams(USE_MIRRORS, "true");
    }
    if (useAlternative) {
      runningBuild.sharedConfigParams(USE_ALTERNATES, "true");
    }
    fetchGitHub(runningBuild.withAgentConfiguration(myAgentConfiguration).build());
  }

  private void fetchGitHub(final AgentRunningBuild build) throws VcsException {
    String versionFirst = "1d45e81f92970f025a8699915b32496a0b6885bd";
    CheckoutRules rules = new CheckoutRules("");
    myVcsSupport.updateSources(myRoot, rules, versionFirst, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).contains("README.md");
    checkConfig(false, build);

    String versionNext = "ec6baf656c6f3a4f090ed245b423b893e0226264";
    myVcsSupport.updateSources(myRoot, rules, versionNext, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).contains("README.md");
    checkConfig(false, build);
  }

  private void writeCertificate() throws Exception {
    new SSLTestUtil().writeAnotherCert(myHomeDirectory);
  }

  private void checkConfig(boolean shouldCAInfo, AgentRunningBuild build) throws VcsException {
    final AgentGitFacade gitFacade = gitFacade(build);
    try {
      final String sslCAInfo = gitFacade.getConfig().setPropertyName("http.sslCAInfo").call();
      if (shouldCAInfo) {
        Assert.assertNotNull(sslCAInfo);
      } else {
        Assert.assertNotNull(StringUtil.nullIfEmpty(sslCAInfo));
      }
    } catch (Exception e) {
      if (shouldCAInfo) {
        Assert.fail("ssl CA info have to be but not exists");
      }
    }
  }

  private AgentGitFacade gitFacade(AgentRunningBuild build) throws VcsException {
    final AgentPluginConfig config = pluginConfigFactory().createConfig(build, myRoot);
    final Map<String, String> env = getGitCommandEnv(config, build);
    final GitFactory gitFactory = gitMetaFactory().createFactory(getGitAgentSSHService(), new BuildContext(build, config));
    return gitFactory.create(myCheckoutDir);
  }

  private GitAgentSSHService getGitAgentSSHService() {
    return myAgentSupportBuilder.getGitAgentSSHService();
  }

  private GitMetaFactory gitMetaFactory() {
    return myAgentSupportBuilder.getGitMetaFactory();
  }

  private PluginConfigFactory pluginConfigFactory() {
    return myAgentSupportBuilder.getPluginConfigFactory();
  }

  private Map<String, String> getGitCommandEnv(@NotNull AgentPluginConfig config, @NotNull AgentRunningBuild build) {
    if (config.isRunGitWithBuildEnv()) {
      return build.getBuildParameters().getEnvironmentVariables();
    } else {
      return new HashMap<>(0);
    }
  }
}
