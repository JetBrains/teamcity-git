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
import jetbrains.buildServer.agent.vcs.AgentCheckoutAbility;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.HashCalculatorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.UpdaterImpl.GIT_WITH_SPARSE_CHECKOUT;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder.agentConfiguration;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class AutoCheckoutTest extends BaseRemoteRepositoryTest {

  private GitAgentVcsSupport myVcsSupport;
  private File myCheckoutDir;

  public AutoCheckoutTest() {
    super("repo.git");
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myCheckoutDir = myTempFiles.createTempDir();
  }

  public void git_client_found_by_path_from_root() throws IOException, VcsException {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot = vcsRootWithAgentGitPath("git");

    verifyCanCheckout(vcsRoot, CheckoutRules.DEFAULT, runningBuild().build());
  }

  public void client_found_by_path_from_environment() throws IOException, VcsException {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot = vcsRootWithAgentGitPath(null);
    AgentRunningBuild build = runningBuild().sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, "git").build();

    verifyCanCheckout(vcsRoot, CheckoutRules.DEFAULT, build);
  }

  public void git_client_not_found_by_path_from_root() throws IOException {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot =  vcsRootWithAgentGitPath("gitt");

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, CheckoutRules.DEFAULT, runningBuild().build());
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCheckoutAbility.NO_VCS_CLIENT);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Unable to run git at path gitt");
  }

  public void exclude_rules_are_used_without_sparse_checkout() throws IOException, VcsException {
    myVcsSupport = vcsSupportWithFakeGitOfVersion(GIT_WITH_SPARSE_CHECKOUT);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath("git");
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "false").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("-:dir/q.txt"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCheckoutAbility.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Exclude rules are not supported for agent checkout");
  }

  public void include_rule_with_mapping_is_used_without_sparse_checkout() throws IOException, VcsException {
    myVcsSupport =  vcsSupportWithFakeGitOfVersion(GIT_WITH_SPARSE_CHECKOUT);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath("git");
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "false").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("+:a/b/c => d"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCheckoutAbility.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Agent checkout for the git supports only include rule of form '. => subdir'");
  }

  public void git_version_does_not_support_sparse_checkout() throws IOException, VcsException {
    myVcsSupport =  vcsSupportWithFakeGitOfVersion(GIT_WITH_SPARSE_CHECKOUT.previousVersion());

    VcsRoot vcsRoot = vcsRootWithAgentGitPath("git");
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("-:dir/q.txt"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCheckoutAbility.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Exclude rules are not supported for agent checkout");
  }

  private void verifyCanCheckout(final VcsRoot vcsRoot, CheckoutRules checkoutRules, final AgentRunningBuild build) throws VcsException {
    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, checkoutRules, build);
    then(canCheckout.getCanNotCheckoutReason()).isNull();

    //do actual checkout and ensure that it completes without errors
    FileUtil.delete(myCheckoutDir);
    myCheckoutDir.mkdirs();
    myVcsSupport.updateSources(vcsRoot, checkoutRules, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, true);
    then(myCheckoutDir.listFiles()).isNotEmpty();
  }

  private VcsRoot vcsRootWithAgentGitPath(String path) {
    return vcsRoot().withBranch("refs/heads/master").withAgentGitPath(path).withFetchUrl(getRemoteRepositoryUrl("repo.git")).build();
  }

  @NotNull
  private GitAgentVcsSupport vcsSupportWithRealGit() throws IOException {
    GitDetector detector = new GitDetectorImpl(new MockGitPathResolver());
    return createVcsSupport(detector);
  }

  @NotNull
  private GitAgentVcsSupport vcsSupportWithFakeGitOfVersion(final GitVersion version) throws IOException {
    GitDetector detector = new GitDetector() {
      @NotNull
      public GitExec getGitPathAndVersion(@NotNull final VcsRoot root, @NotNull final BuildAgentConfiguration config, @NotNull final AgentRunningBuild build) throws VcsException {
        return new GitExec("git", version);
      }
    };
    return createVcsSupport(detector);
  }

  @NotNull
  private GitAgentVcsSupport createVcsSupport(final GitDetector detector) throws IOException {
    BuildAgentConfiguration agentConfiguration = agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir()).build();
    MirrorManager mirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(agentConfiguration), new HashCalculatorImpl());
    VcsRootSshKeyManagerProvider provider = new MockVcsRootSshKeyManagerProvider();
    GitMetaFactory metaFactory = new GitMetaFactoryImpl();
    final BuildAgent agent = new MockBuildAgent();
    return new GitAgentVcsSupport(new MockDirectoryCleaner(),
                                  new GitAgentSSHService(agent, agentConfiguration, new MockGitPluginDescriptor(), provider),
                                  new PluginConfigFactoryImpl(agentConfiguration, detector), mirrorManager, metaFactory);
  }
}
