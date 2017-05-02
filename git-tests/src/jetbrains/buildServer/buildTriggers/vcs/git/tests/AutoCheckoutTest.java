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

import jetbrains.buildServer.agent.AgentCanNotCheckoutReason;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.vcs.AgentCheckoutAbility;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.UpdaterImpl.GIT_WITH_SPARSE_CHECKOUT;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitVersionProvider.getGitPath;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ALL")
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

    VcsRoot vcsRoot = vcsRootWithAgentGitPath();

    verifyCanCheckout(vcsRoot, CheckoutRules.DEFAULT, runningBuild().addRoot(vcsRoot).build());
  }

  public void client_found_by_path_from_environment() throws IOException, VcsException {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot = vcsRootWithAgentGitPath(null);
    AgentRunningBuild build = runningBuild().sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, getGitPath()).addRoot(vcsRoot).build();

    verifyCanCheckout(vcsRoot, CheckoutRules.DEFAULT, build);
  }

  public void git_client_not_found_by_path_from_root() throws IOException {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot =  vcsRootWithAgentGitPath("gitt");

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, CheckoutRules.DEFAULT, runningBuild().addRoot(vcsRoot).build());
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NO_VCS_CLIENT);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Unable to run git at path gitt");
  }

  public void exclude_rules_are_used_without_sparse_checkout() throws IOException, VcsException {
    myVcsSupport = vcsSupportWithFakeGitOfVersion(GIT_WITH_SPARSE_CHECKOUT);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath();
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "false")
      .addRootEntry(vcsRoot, "-:dir/q.txt").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("-:dir/q.txt"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Cannot perform sparse checkout using git " + GIT_WITH_SPARSE_CHECKOUT);
  }

  public void include_rule_with_mapping_is_used_without_sparse_checkout() throws IOException, VcsException {
    myVcsSupport =  vcsSupportWithFakeGitOfVersion(GIT_WITH_SPARSE_CHECKOUT);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath();
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "false")
      .addRootEntry(vcsRoot, "+:a/b/c => d").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("+:a/b/c => d"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Unsupported rules for agent-side checkout: +:a/b/c => d");
  }

  public void git_version_does_not_support_sparse_checkout() throws IOException, VcsException {
    GitVersion gitVersion = GIT_WITH_SPARSE_CHECKOUT.previousVersion();
    myVcsSupport =  vcsSupportWithFakeGitOfVersion(gitVersion);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath(getGitPath());
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
      .addRootEntry(vcsRoot, "-:dir/q.txt").build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, new CheckoutRules("-:dir/q.txt"), build);
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains("Cannot perform sparse checkout using git " + gitVersion);
  }

  public void git_version_does_not_support_sparse_checkout_default_rules() throws IOException, VcsException {
    GitVersion gitVersion = GIT_WITH_SPARSE_CHECKOUT.previousVersion();
    myVcsSupport =  vcsSupportWithFakeGitOfVersion(gitVersion);

    VcsRoot vcsRoot = vcsRootWithAgentGitPath(gitVersion.toString());
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true").addRoot(vcsRoot).build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, CheckoutRules.DEFAULT, build);
    then(canCheckout.getCanNotCheckoutReason()).isNull();
  }

  public void should_check_auth_method() throws Exception {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot vcsRoot = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withAuthMethod(AuthenticationMethod.PRIVATE_KEY_FILE)
      .withAgentGitPath(getGitPath())
      .build();

    AgentCheckoutAbility canCheckout = myVcsSupport.canCheckout(vcsRoot, CheckoutRules.DEFAULT, runningBuild().addRoot(vcsRoot).build());
    then(canCheckout.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.UNKNOWN_REASON_TYPE);
    then(canCheckout.getCanNotCheckoutReason().getDetails()).contains(
      "TeamCity doesn't support authentication method 'Private Key' with agent checkout. Please use different authentication method.");
  }

  @DataProvider
  public static Object[][] severalRootsSetups() throws Exception {
    return new Object[][]{
      new Object[]{new Setup()
        .setShouldFail(true)
      },
      new Object[]{new Setup()
        .setCheckoutRules1("+:dir")
        .setCheckoutRules2("+:dir")
        .setShouldFail(true)
      },
      new Object[]{new Setup()
        .setCheckoutRules2("+:dir2")
        .setShouldFail(true)
      },
      new Object[]{new Setup()
        .setCheckoutRules2("+:dir1") //even though we checkout different dirs, .git of both repositories is located in the same dir
        .setCheckoutRules2("+:dir2")
        .setShouldFail(true)
      },
      new Object[]{new Setup()
        .setCheckoutRules1("+:.=>dir1")
        .setCheckoutRules2("+:.=>dir2")
        .setShouldFail(false)
      }
    };
  }


  @TestFor(issues = "TW-49786")
  @Test(dataProvider = "severalRootsSetups")
  public void several_roots(@NotNull Setup setup) throws Exception {
    myVcsSupport = vcsSupportWithRealGit();

    VcsRoot root1 = vcsRoot().withId(1).withFetchUrl("http://some.org/repo1.git").withAgentGitPath(getGitPath()).build();
    VcsRoot root2 = vcsRoot().withId(2).withFetchUrl("http://some.org/repo2.git").withAgentGitPath(getGitPath()).build();
    AgentRunningBuild build = runningBuild()
      .addRootEntry(root1, setup.getCheckoutRules1())
      .addRootEntry(root2, setup.getCheckoutRules2())
      .build();
    AgentCheckoutAbility canCheckout1 = myVcsSupport.canCheckout(root1, new CheckoutRules(setup.getCheckoutRules1()), build);
    AgentCheckoutAbility canCheckout2 = myVcsSupport.canCheckout(root2, new CheckoutRules(setup.getCheckoutRules2()), build);
    if (setup.isShouldFail()) {
      then(canCheckout1.getCanNotCheckoutReason().getDetails()).contains(
        "Cannot checkout VCS root '" + root1.getName() + "' into the same directory as VCS root '" + root2.getName() + "'");
      then(canCheckout2.getCanNotCheckoutReason().getDetails()).contains(
        "Cannot checkout VCS root '" + root2.getName() + "' into the same directory as VCS root '" + root1.getName() + "'");
    } else {
      then(canCheckout1.getCanNotCheckoutReason()).isNull();
      then(canCheckout2.getCanNotCheckoutReason()).isNull();
    }
  }


  @TestFor(issues = "TW-49786")
  public void should_respect_root_settings_when_checking_multi_root_constraints() throws Exception {
    myVcsSupport = vcsSupportWithRealGit();

    //second root has broken git path, we should not take it into account
    //during canCheckout() for the first VCS root
    VcsRoot root1 = vcsRoot().withId(1).withAgentGitPath(getGitPath()).withFetchUrl("http://some.org/repo1.git").build();
    VcsRoot root2 = vcsRoot().withId(2).withAgentGitPath("wrongGitPath").withFetchUrl("http://some.org/repo2.git").build();
    AgentRunningBuild build = runningBuild()
      .addRootEntry(root1, "+:dir1")
      .addRootEntry(root2, "+:dir2")
      .build();

    AgentCheckoutAbility canCheckout1 = myVcsSupport.canCheckout(root1, new CheckoutRules("+:dir1"), build);
    AgentCheckoutAbility canCheckout2 = myVcsSupport.canCheckout(root2, new CheckoutRules("+:dir2"), build);
    then(canCheckout1.getCanNotCheckoutReason()).isNull();
    then(canCheckout2.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NO_VCS_CLIENT);
    then(canCheckout2.getCanNotCheckoutReason().getDetails()).contains("Unable to run git at path wrongGitPath");
  }


  @TestFor(issues = "TW-49786")
  public void should_respect_root_settings_when_checking_multi_root_constraints2() throws Exception {
    VcsRoot root1 = vcsRoot().withId(1).withFetchUrl("http://some.org/repo1.git").build();
    VcsRoot root2 = vcsRoot().withId(2).withFetchUrl("http://some.org/repo2.git").build();
    AgentRunningBuild build = runningBuild()
      .addRootEntry(root1, "+:dir1")
      .addRootEntry(root2, "+:dir2")
      .build();

    //both roots require sparse checkout and mapped into the same directory, but the second
    //root uses git version which doesn't support sparse checkout; we shouldn't take it into
    //account during canCheckout() check for the first root
    GitDetector detector = new GitDetector() {
      @NotNull
      public GitExec getGitPathAndVersion(@NotNull VcsRoot root, @NotNull BuildAgentConfiguration config, @NotNull AgentRunningBuild build) throws VcsException {
        if (root.equals(root1)) {
          return new GitExec("git1", GIT_WITH_SPARSE_CHECKOUT);
        }
        if (root.equals(root2)) {
          return new GitExec("git2", GIT_WITH_SPARSE_CHECKOUT.previousVersion());
        }
        throw new VcsException("Unexpected VCS root");
      }
    };
    myVcsSupport = createVcsSupport(detector);

    AgentCheckoutAbility canCheckout1 = myVcsSupport.canCheckout(root1, new CheckoutRules("+:dir1"), build);
    AgentCheckoutAbility canCheckout2 = myVcsSupport.canCheckout(root2, new CheckoutRules("+:dir2"), build);
    then(canCheckout1.getCanNotCheckoutReason()).isNull();
    then(canCheckout2.getCanNotCheckoutReason().getType()).isEqualTo(AgentCanNotCheckoutReason.NOT_SUPPORTED_CHECKOUT_RULES);
    then(canCheckout2.getCanNotCheckoutReason().getDetails()).contains("Cannot perform sparse checkout using git " + GIT_WITH_SPARSE_CHECKOUT.previousVersion());
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

  private VcsRoot vcsRootWithAgentGitPath() {
    return vcsRootWithAgentGitPath(getGitPath());
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
    return new AgentSupportBuilder(myTempFiles).setGitDetector(detector).build();
  }


  private static class Setup {
    private String myCheckoutRules1 = CheckoutRules.DEFAULT.getAsString();
    private String myCheckoutRules2 = CheckoutRules.DEFAULT.getAsString();
    private boolean myShouldFail;

    @NotNull
    public String getCheckoutRules1() {
      return myCheckoutRules1;
    }

    @NotNull
    public Setup setCheckoutRules1(@NotNull String checkoutRules1) {
      myCheckoutRules1 = checkoutRules1;
      return this;
    }

    @NotNull
    public String getCheckoutRules2() {
      return myCheckoutRules2;
    }

    @NotNull
    public Setup setCheckoutRules2(@NotNull String checkoutRules2) {
      myCheckoutRules2 = checkoutRules2;
      return this;
    }

    public boolean isShouldFail() {
      return myShouldFail;
    }

    @NotNull
    public Setup setShouldFail(boolean shouldFail) {
      myShouldFail = shouldFail;
      return this;
    }

    @Override
    public String toString() {
      return "rules1: '" + myCheckoutRules1 + "', rules2: '" + myCheckoutRules2 + "'";
    }
  }
}
