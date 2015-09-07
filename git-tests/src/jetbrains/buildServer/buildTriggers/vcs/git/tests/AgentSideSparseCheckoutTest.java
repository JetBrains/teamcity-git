/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.HashCalculatorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder.agentConfiguration;
import static org.testng.AssertJUnit.*;

@Test
public class AgentSideSparseCheckoutTest extends BaseRemoteRepositoryTest {

  private GitAgentVcsSupport myVcsSupport;
  private File myCheckoutDir;
  private VcsRoot myRoot;

  public AgentSideSparseCheckoutTest() {
    super("repo.git");
  }


  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myCheckoutDir = myTempFiles.createTempDir();
    String pathToGit = getGitPath();
    GitPathResolver resolver = new MockGitPathResolver(pathToGit);
    GitDetector detector = new GitDetectorImpl(resolver);
    BuildAgentConfiguration agentConfiguration = agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir()).build();
    PluginConfigFactoryImpl pluginConfigFactory = new PluginConfigFactoryImpl(agentConfiguration, detector);
    MirrorManager mirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(agentConfiguration), new HashCalculatorImpl());
    VcsRootSshKeyManagerProvider provider = new MockVcsRootSshKeyManagerProvider();
    GitMetaFactory metaFactory = new GitMetaFactoryImpl();
    final BuildAgent agent = new MockBuildAgent();
    myVcsSupport = new GitAgentVcsSupport(new MockDirectoryCleaner(),
                                          new GitAgentSSHService(agent, agentConfiguration, new MockGitPluginDescriptor(), provider),
                                          pluginConfigFactory, mirrorManager, metaFactory);
    myRoot = vcsRoot()
      .withAgentGitPath(pathToGit)
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
  }


  public void exclude_dir() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules("-:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertFalse(new File(myCheckoutDir, "dir").exists());
  }


  public void exclude_file() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules(asList("-:readme.txt", "-:dir/q.txt"));
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertFalse(new File(myCheckoutDir, "readme.txt").exists());
    assertFalse(new File(myCheckoutDir, "dir/q.txt").exists());
  }


  public void include_dir() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules("+:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    String[] files = myCheckoutDir.list();
    assertEquals(2, files.length);
    assertEquals(".git", files[0]);
    assertEquals("dir", files[1]);
  }


  public void exclude_inside_include() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules(asList("+:dir", "-:dir/q.txt"));
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertTrue(new File(myCheckoutDir, "dir").exists());
    assertFalse(new File(myCheckoutDir, "dir/q.txt").exists());
    assertFalse(new File(myCheckoutDir, "readme.txt").exists());
  }


  public void update_files_after_checkout_rules_change() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules("+:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertTrue(new File(myCheckoutDir, "dir").exists());
    assertFalse(new File(myCheckoutDir, "readme.txt").exists());

    rules = new CheckoutRules("-:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertFalse(new File(myCheckoutDir, "dir").exists());
    assertTrue(new File(myCheckoutDir, "readme.txt").exists());
  }


  public void update_files_after_switching_to_default_rules() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();
    CheckoutRules rules = new CheckoutRules("+:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertTrue(new File(myCheckoutDir, "dir").exists());
    assertFalse(new File(myCheckoutDir, "readme.txt").exists());

    rules = CheckoutRules.DEFAULT;
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    assertTrue(new File(myCheckoutDir, "dir").exists());
    assertTrue(new File(myCheckoutDir, "readme.txt").exists());
  }


  private String getGitPath() throws IOException {
    String providedGit = System.getenv(Constants.TEAMCITY_AGENT_GIT_PATH);
    if (providedGit != null) {
      return providedGit;
    } else {
      return "git";
    }
  }
}
