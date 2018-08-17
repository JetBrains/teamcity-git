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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.TestInternalProperties;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.AgentRuntimeProperties;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LsRemoteCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitVersionProvider.getGitPath;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static jetbrains.buildServer.util.FileUtil.writeFileAndReportErrors;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentVcsSupportTest {

  private TempFiles myTempFiles;
  private File myMainRepo;
  private File myCheckoutDir;
  private VcsRootImpl myRoot;
  private int myVcsRootId = 0;
  private GitAgentVcsSupport myVcsSupport;
  private AgentRunningBuild myBuild;
  private AgentSupportBuilder myBuilder;

  @BeforeMethod
  public void setUp() throws Exception {
    TestInternalProperties.init();
    myTempFiles = new TempFiles();

    File repositoriesDir = myTempFiles.createTempDir();

    File masterRep = dataFile("repo.git");
    myMainRepo = new File(repositoriesDir, "repo.git");
    copyRepository(masterRep, myMainRepo);

    File submoduleRep = dataFile("submodule.git");
    copyRepository(submoduleRep, new File(repositoriesDir, "submodule.git"));

    File submoduleRep2 = dataFile("sub-submodule.git");
    copyRepository(submoduleRep2, new File(repositoriesDir, "sub-submodule.git"));

    myCheckoutDir = myTempFiles.createTempDir();
    myBuilder = new AgentSupportBuilder(myTempFiles);
    myVcsSupport = myBuilder.build();
    myBuild = createRunningBuild(true);
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).build();
  }


  @AfterMethod
  protected void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  @TestFor(issues = "TW-33401")
  @Test(dataProvider = "mirrors")
  public void should_not_remove_remote_tracking_branches(Boolean useMirrors) throws Exception {
    VcsRootSshKeyManagerProvider provider = new VcsRootSshKeyManagerProvider() {
      @Nullable
      public VcsRootSshKeyManager getSshKeyManager() {
        return null;
      }
    };
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();

    GitAgentVcsSupport git = myBuilder.setSshKeyProvider(provider).setGitMetaFactory(loggingFactory).build();

    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, useMirrors.toString()));

    git.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);
    loggingFactory.clear();

    //we already have everything we need for this update, no fetch should be executed
    git.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    assertFalse("Refs removed: " + loggingFactory.getInvokedMethods(UpdateRefCommand.class),
                loggingFactory.getInvokedMethods(UpdateRefCommand.class).contains("delete"));
    assertTrue("Redundant fetch", loggingFactory.getInvokedMethods(FetchCommand.class).isEmpty());
  }


  @TestFor(issues = "TW-42249")
  public void should_not_invoke_fetch_in_working_dir_after_clean_checkout() throws Exception {
    VcsRootSshKeyManagerProvider provider = new VcsRootSshKeyManagerProvider() {
      @Nullable
      public VcsRootSshKeyManager getSshKeyManager() {
        return null;
      }
    };
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    GitAgentVcsSupport git = myBuilder.setSshKeyProvider(provider).setGitMetaFactory(loggingFactory).build();

    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY,
                                                     PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES));

    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    git.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    assertEquals("Redundant fetch", 1, loggingFactory.getNumberOfCalls(FetchCommand.class));
  }


  @TestFor(issues = {"TW-42551", "TW-46857"})
  public void should_set_remote_tracking_branch() throws Exception {
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY,
                                                     PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES));

    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    then(new BranchConfig(r.getConfig(), "master").getRemoteTrackingBranch()).isEqualTo("refs/remotes/origin/master");

    //TW-46857
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withBranch("personal-branch2").withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "3df61e6f11a5a9b919cb3f786a83fdd09f058617", myCheckoutDir, build, false);
    then(new BranchConfig(r.getConfig(), "personal-branch2").getRemoteTrackingBranch()).isEqualTo("refs/remotes/origin/personal-branch2");
  }


  @TestFor(issues = "TW-46854")
  @Test(dataProvider = "mirrors")
  public void should_update_remote_tracking_branch_in_case_of_fast_forward_update(boolean useMirrors) throws Exception {
    File remoteRepo = myTempFiles.createTempDir();

    copyRepository(dataFile("repo_for_fetch.2"), remoteRepo);
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(remoteRepo).withUseMirrors(useMirrors).build();
    String buildBranchParam = GitUtils.getGitRootBranchParamName(root);

    //run build in master branch
    AgentRunningBuild build = createRunningBuild(map(buildBranchParam, "refs/heads/master"));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);

    //fast-forward update master to point to the same commit as master
    Repository remote = new RepositoryBuilder().setGitDir(remoteRepo).build();
    RefUpdate refUpdate = remote.updateRef("refs/heads/personal");
    refUpdate.setNewObjectId(ObjectId.fromString("add81050184d3c818560bdd8839f50024c188586"));
    refUpdate.update();

    //run build in personal branch
    build = createRunningBuild(map(buildBranchParam, "refs/heads/personal"));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, build, false);

    //fast-forward update personal branch to point to the same commit as master
    refUpdate = remote.updateRef("refs/heads/personal");
    refUpdate.setNewObjectId(ObjectId.fromString("d47dda159b27b9a8c4cee4ce98e4435eb5b17168"));
    refUpdate.update();

    //run build on updated personal branch
    build = createRunningBuild(map(buildBranchParam, "refs/heads/personal"));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);

    //both branch and its remote-tracking branch should be updated
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    then(r.getAllRefs().get("refs/heads/personal").getObjectId().name()).isEqualTo("d47dda159b27b9a8c4cee4ce98e4435eb5b17168");
    then(r.getAllRefs().get("refs/remotes/origin/personal").getObjectId().name()).isEqualTo("d47dda159b27b9a8c4cee4ce98e4435eb5b17168");
  }


  /**
   * Test work normally if .git/index.lock file exists
   */
  public void testRecoverIndexLock() throws Exception {
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD,
                               myCheckoutDir, myBuild, false);

    //emulate incorrect git termination (in this it could leave index.lock file)
    FileUtil.copy(new File(myCheckoutDir, ".git" + File.separator + "index"),
                  new File(myCheckoutDir, ".git" + File.separator + "index.lock"));

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.CUD1_VERSION, myCheckoutDir, myBuild, false);
  }


  /**
   * Test work normally if .git/refs/heads/<branch>.lock file exists
   */
  public void testRecoverRefLock() throws Exception {
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    String firstCommitInPatchTests = GitUtils.makeVersion("a894d7d58ffde625019a9ecf8267f5f1d1e5c341", 1245766034000L);
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);

    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    //emulate incorrect git termination (in this it could leave refs/heads/<branch-name>.lock file)
    FileUtil.createIfDoesntExist(new File(myCheckoutDir, ".git" + File.separator + GitUtils.expandRef("master") + ".lock"));
    //should recover from locked ref if previous checkout was on the same branch:
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);

    //emulate incorrect git termination (in this it could leave refs/heads/<branch-name>.lock file)
    FileUtil.createIfDoesntExist(new File(myCheckoutDir, ".git" + File.separator + GitUtils.expandRef("patch-tests") + ".lock"));
    //should recover from locked ref if previous checkout was on a different branch:
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);
  }


  @TestFor(issues = "TW-31381")
  public void recover_from_ref_lock_during_fetch() throws Exception {
    File repo = dataFile("repo_for_fetch.2.personal");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    final String fetchUrl = GitUtils.toURL(remoteRepo);
    VcsRootImpl root = vcsRoot().withBranch("refs/heads/master").withAgentGitPath(getGitPath()).withFetchUrl(fetchUrl).build();
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, myBuild, false);

    //update remote branch master
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2");
    copyRepository(updatedRepo, remoteRepo);

    File mirror = myBuilder.getMirrorManager().getMirrorDir(fetchUrl);
    FileUtil.createIfDoesntExist(new File(mirror, "refs/heads/master.lock"));
    FileUtil.createIfDoesntExist(new File(myCheckoutDir, ".git/refs/heads/master.lock"));
    FileUtil.createIfDoesntExist(new File(myCheckoutDir, ".git/refs/remotes/origin/master.lock"));


    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, myBuild, false);
  }


  @TestFor(issues = "TW-31039")
  @Test(dataProvider = "mirrors")
  public void build_on_pull_request(Boolean useMirrors) throws Exception {
    //Remote repo contains a pull request branch refs/changes/2/1 which is not under refs/heads/*,
    //this branch points to a commit which is not reachable from the default branch in vcs root
    //and from any other branches under refs/heads/.

    //Ensure that once we pass a pull request branch name, checkout it successful
    VcsRootImpl root = createRoot(myMainRepo, "master");
    String pullRequestCommit = "ea5e05051fbfaa7d8da97586807b009cbfebae9d";
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors),
                                                     GitUtils.getGitRootBranchParamName(root), "refs/changes/2/1"));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, pullRequestCommit, myCheckoutDir, build, false);
  }


  /**
   * Test checkout submodules on agent. Machine that runs this test should have git installed.
   */
  public void testSubmodulesCheckout() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.SUBMODULE_ADDED_VERSION,
                               myCheckoutDir, myBuild, false);

    assertTrue(new File(myCheckoutDir, "submodule" + File.separator + "file.txt").exists());
  }


  /**
   * Test non-recursive submodules checkout: submodules of submodules are not retrieved
   */
  public void testSubSubmodulesCheckoutNonRecursive() throws Exception {
    testSubSubmoduleCheckout(false);
  }


  /**
   * Test recursive submodules checkout: submodules of submodules are retrieved
   */
  public void testSubSubmodulesCheckoutRecursive() throws Exception {
    testSubSubmoduleCheckout(true);
  }


  @TestFor(issues = "TW-27043")
  public void clean_files_in_submodules() throws Exception {
    //vcs root with submodules which cleans all untracked files on every build:
    myRoot.addProperty(Constants.BRANCH_NAME, "sub-submodule");
    myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    myRoot.addProperty(Constants.AGENT_CLEAN_FILES_POLICY, AgentCleanFilesPolicy.ALL_UNTRACKED.name());
    myRoot.addProperty(Constants.AGENT_CLEAN_POLICY, AgentCleanPolicy.ALWAYS.name());

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "ce6044093939bb47283439d97a1c80f759669ff5", myCheckoutDir, myBuild, false);

    //create untracked files inside submodules
    File submoduleDir = new File(myCheckoutDir, "first-level-submodule");
    File subSubmoduleDir = new File(submoduleDir, "sub-sub");
    File untrackedFileSubmodule = new File(submoduleDir, "untracked");
    File untrackedFileSubSubmodule = new File(subSubmoduleDir, "untracked");
    assertTrue(untrackedFileSubmodule.createNewFile());
    assertTrue(untrackedFileSubSubmodule.createNewFile());

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "ce6044093939bb47283439d97a1c80f759669ff5", myCheckoutDir, myBuild, false);

    assertFalse(untrackedFileSubmodule.exists());
    assertFalse(untrackedFileSubSubmodule.exists());
  }


  @DataProvider(name = "ignoredLongFileNames")
  public Object[][] ignoredLongFileNames() {
    return new Object[][] {{true}, {false}};
  }

  @TestFor(issues = "TW-35545")
  @Test(dataProvider = "ignoredLongFileNames")
  public void clean_files_with_long_names(Boolean filesWithLongNamesIgnored) throws Exception {
    myRoot.addProperty(Constants.AGENT_CLEAN_FILES_POLICY, AgentCleanFilesPolicy.ALL_UNTRACKED.name());
    myRoot.addProperty(Constants.AGENT_CLEAN_POLICY, AgentCleanPolicy.ALWAYS.name());

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, myBuild, false);

    File dirWithLongName = new File(myCheckoutDir, "dirWithLongName");
    for (int i = 0; i < 20; i++) {
      dirWithLongName = new File(dirWithLongName, "dirWithLongName");
    }
    dirWithLongName.mkdirs();

    File fileWithLongName = new File(dirWithLongName, "test");
    writeFileAndReportErrors(fileWithLongName, "test");

    if (filesWithLongNamesIgnored) {
      File exclude = new File(myCheckoutDir, ".git/info/exclude".replaceAll("/", Matcher.quoteReplacement(File.separator)));
      writeFileAndReportErrors(exclude, "dirWithLongName\n");
    }

    assertTrue(fileWithLongName.exists());

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, myBuild, false);

    assertFalse(fileWithLongName.exists());
  }


  public void should_create_bare_repository_in_caches_dir() throws Exception {
    File mirrorsDir = myBuilder.getAgentConfiguration().getCacheDirectory("git");
    assertTrue(mirrorsDir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        return f.isDirectory();
      }
    }).length == 0);

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    GitVcsRoot root = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot);
    File bareRepositoryDir = root.getRepositoryDir();
    assertTrue(bareRepositoryDir.exists());
    //check some dirs that should be present in the bare repository:
    File objectsDir = new File(bareRepositoryDir, "objects");
    assertTrue(new File(bareRepositoryDir, "info").exists());
    assertTrue(objectsDir.exists());
    assertTrue(new File(bareRepositoryDir, "refs").exists());

    String config = FileUtil.loadTextAndClose(new FileReader(new File(bareRepositoryDir, "config")));
    assertTrue(config.contains("[remote \"origin\"]"));
    String remoteUrl = "url = " + root.getRepositoryFetchURL();
    assertTrue(config.contains(remoteUrl));

    File packDir = new File(objectsDir, "pack");
    boolean looseObjectsExists = objectsDir.listFiles().length > 2;//2 - because there are 2 dirs there: info and pack
    boolean packFilesExists = packDir.listFiles().length >=2; //at least one pack file with its index exists
    boolean fetchWasDone = looseObjectsExists || packFilesExists;
    assertTrue(fetchWasDone);
  }


  public void old_cloned_repository_should_use_local_mirror() throws Exception {
    AgentRunningBuild buildBeforeUsingMirrors = createRunningBuild(false);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildBeforeUsingMirrors, false);
    AgentRunningBuild buildWithMirrorsEnabled = createRunningBuild(true);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildWithMirrorsEnabled, false);
    GitVcsRoot root = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot);
    String localMirrorUrl = new URIish(root.getRepositoryDir().toURI().toASCIIString()).toString();
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertEquals(root.getRepositoryFetchURL().toString(), r.getConfig().getString("url", localMirrorUrl, "insteadOf"));
  }


  public void do_not_use_mirror_if_agent_property_set_to_false() throws Exception {
    AgentRunningBuild build2 = createRunningBuild(false);
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);
    File gitConfigFile = new File(myCheckoutDir, ".git" + File.separator + "config");
    String config = FileUtil.loadTextAndClose(new FileReader(gitConfigFile));
    assertFalse(config, config.contains("insteadOf"));
  }


  public void stop_use_mirror_if_agent_property_changed_to_false() throws Exception {
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    AgentRunningBuild build2 = createRunningBuild(false);
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);

    File gitConfigFile = new File(myCheckoutDir, ".git" + File.separator + "config");
    String config = FileUtil.loadTextAndClose(new FileReader(gitConfigFile));
    assertFalse(config, config.contains("insteadOf"));
  }


  public void stop_use_any_mirror_if_agent_property_changed_to_false() throws Exception {
    AgentRunningBuild build2 = createRunningBuild(false);
    GitVcsRoot root = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot);
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);

    //add some mirror
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    StoredConfig config = r.getConfig();
    config.setString("url", "/some/path", "insteadOf", root.getRepositoryFetchURL().toString());
    config.save();

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);
    config = new RepositoryBuilder().setWorkTree(myCheckoutDir).build().getConfig();
    assertTrue(config.getSubsections("url").isEmpty());
  }


  public void stop_using_alternates_when_mirrors_are_disabled_in_vcs_root_option() throws Exception {
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, myBuild, false);

    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(false).build();
    AgentRunningBuild build2 = createRunningBuild(false);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build2, false);

    assertFalse("Build uses alternates when they disabled in VCS root settings",
                new File(myCheckoutDir, ".git/objects/info/alternates").exists());
  }


  public void stop_using_alternates_when_mirror_strategy_changed() throws Exception {
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, myBuild, false);

    AgentRunningBuild build2 = createRunningBuild(
      map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY, PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY));
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build2, false);

    assertFalse("Build uses alternates when they disabled in VCS root settings",
                new File(myCheckoutDir, ".git/objects/info/alternates").exists());
  }


  public void stop_using_mirrors_when_mirrors_are_disabled_in_vcs_root_option() throws Exception {
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(true).build();
    AgentRunningBuild build1 = createRunningBuild(map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY, PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY));
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build1, false);

    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(false).build();
    AgentRunningBuild build2 = createRunningBuild(false);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build2, false);

    StoredConfig config = new RepositoryBuilder().setWorkTree(myCheckoutDir).build().getConfig();
    assertTrue(config.getSubsections("url").isEmpty());
  }


  @TestFor(issues = "TW-25839")
  public void update_should_not_fail_if_local_mirror_is_corrupted() throws Exception {
    AgentRunningBuild buildWithMirrorsEnabled = createRunningBuild(true);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildWithMirrorsEnabled, false);

    //corrupt local mirror
    GitVcsRoot root = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot);
    File mirror = myBuilder.getMirrorManager().getMirrorDir(root.getRepositoryFetchURL().toString());
    File[] children = mirror.listFiles();
    if (children != null) {
      for (File child : children) {
        delete(child);
      }
    }

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildWithMirrorsEnabled,
                               false);
  }


  @TestFor(issues = "TW-29291")
  public void shallow_clone_should_check_if_auxiliary_branch_already_exists() throws Exception {
    AgentRunningBuild build = createRunningBuild(new HashMap<String, String>() {{
      put(PluginConfigImpl.USE_MIRRORS, "true");
      put(PluginConfigImpl.USE_SHALLOW_CLONE, "true");
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build, true);

    //manually create a branch tmp_branch_for_build with, it seems like it wasn't removed due to errors in previous checkouts
    GitVcsRoot root = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot);
    File mirror = myBuilder.getMirrorManager().getMirrorDir(root.getRepositoryFetchURL().toString());
    File emptyBranchFile = new File(mirror, "refs" + File.separator + "heads" + File.separator + "tmp_branch_for_build");
    FileUtil.writeToFile(emptyBranchFile, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653\n".getBytes());

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build, true);
  }


  public void when_fetch_for_mirror_failed_remove_it_and_try_again() throws Exception {
    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();

    AgentRunningBuild buildWithMirrors = createRunningBuild(true);
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, buildWithMirrors, false);

    //create branch tmp in the mirror
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));
    Repository r = new RepositoryBuilder().setBare().setGitDir(mirror).build();
    RefUpdate update = r.updateRef("refs/heads/tmp");
    update.setNewObjectId(ObjectId.fromString("add81050184d3c818560bdd8839f50024c188586"));
    update.update();

    //update remote repo
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2.personal");
    copyRepository(updatedRepo, remoteRepo);

    //create branch tmp/1 in remote repo, so fetch will fail
    r = new RepositoryBuilder().setBare().setGitDir(remoteRepo).build();
    update = r.updateRef("refs/heads/tmp/1");
    update.setNewObjectId(ObjectId.fromString("d47dda159b27b9a8c4cee4ce98e4435eb5b17168"));
    update.update();

    //update succeeds
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, buildWithMirrors, false);
  }


  public void should_handle_ref_pointing_to_invalid_object() throws Exception {
    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).withUseMirrors(true).build();

    //first build
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY, PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, build, false);

    //create ref pointing to invalid object
    File gitDir = new File(myCheckoutDir, ".git");
    String invalidObject = "bba7fbcc200b4968e6abd2f7d475dc15306cafc1";
    FileUtil.writeFile(new File(gitDir, "refs/heads/brokenRef"), invalidObject);

    //update remote repo
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.3");
    copyRepository(updatedRepo, remoteRepo);

    //second build
    build = createRunningBuild(map(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY, PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_ALTERNATES));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "bba7fbcc200b4968e6abd2f7d475dc15306cafc6", myCheckoutDir, build, false);
    then(new File(gitDir, "refs/heads/brokenRef")).doesNotExist();
  }


  @TestFor(issues = "TW-43884")
  public void should_remap_mirror_if_its_fetch_and_remove_failed() throws Exception {
    MockFS fs = new MockFS();
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).setFS(fs).build();

    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    //run build to prepare mirror
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, createRunningBuild(true), false);

    //update remote repo: add personal branch
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2.personal");
    copyRepository(updatedRepo, remoteRepo);


    //make first fetch in local mirror to fail:
    AtomicInteger invocationCount = new AtomicInteger(0);
    loggingFactory.addCallback(FetchCommand.class.getName() + ".call", new GitCommandProxyCallback() {
      @Override
      public Optional<Object> call(final Method method, final Object[] args) throws VcsException {
        if (invocationCount.getAndIncrement() == 0)
          throw new VcsException("TEST ERROR");
        return null;
      }
    });
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));

    //try to fetch unknown branch, fetch fails and delete of the mirror also fails
    //build should succeed anyway
    fs.makeDeleteFail(mirror);
    VcsRootImpl root2 = vcsRoot().withAgentGitPath(getGitPath()).withBranch("refs/heads/personal").withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    AgentRunningBuild build = runningBuild()
      .useLocalMirrors(true)
      .sharedConfigParams("teamcity.git.fetchMirrorRetryTimeouts", "")
      .withAgentConfiguration(myBuilder.getAgentConfiguration())
      .build();
    myVcsSupport.updateSources(root2, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);
    File mirrorAfterBuild = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));
    then(mirrorAfterBuild).isNotEqualTo(mirror);//repository was remapped to another dir
  }

  @TestFor(issues = "TW-56415")
  public void should_retry_fetch_mirror() throws Exception {
    MockFS fs = new MockFS();
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).setFS(fs).build();

    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    //run build to prepare mirror
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, createRunningBuild(true), false);

    //update remote repo: add personal branch
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2.personal");
    copyRepository(updatedRepo, remoteRepo);


    //make first two fetches in local mirror to fail:
    AtomicInteger invocationCount = new AtomicInteger(0);
    loggingFactory.addCallback(FetchCommand.class.getName() + ".call", new GitCommandProxyCallback() {
      @Override
      public Optional<Object> call(final Method method, final Object[] args) throws VcsException {
        if (invocationCount.getAndIncrement() <= 1)
          throw new VcsException("TEST ERROR");
        return null;
      }
    });
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));

    //try to fetch unknown branch, first&second fetch fails, third succeeds. If it's not ensure that delete of the mirror also fails
    //build should succeed anyway
    fs.makeDeleteFail(mirror);
    VcsRootImpl root2 = vcsRoot().withAgentGitPath(getGitPath()).withBranch("refs/heads/personal").withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    AgentRunningBuild build = runningBuild()
      .useLocalMirrors(true)
      .sharedConfigParams("teamcity.git.fetchMirrorRetryTimeouts", "0,0")
      .withAgentConfiguration(myBuilder.getAgentConfiguration())
      .build();
    myVcsSupport.updateSources(root2, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);
    File mirrorAfterBuild = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));
    then(mirrorAfterBuild).isEqualTo(mirror);//repository was not remapped to another dir
  }

  @TestFor(issues = "TW-56415")
  public void should_not_retry_fetch_mirror_for_exec_timeout() throws Exception {
    MockFS fs = new MockFS();
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).setFS(fs).build();

    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    //run build to prepare mirror
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    myVcsSupport
      .updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, createRunningBuild(true),
                     false);

    //update remote repo: add personal branch
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2.personal");
    copyRepository(updatedRepo, remoteRepo);

    //make first two fetches in local mirror to fail:
    loggingFactory.addCallback(FetchCommand.class.getName() + ".call", new GitCommandProxyCallback() {
      volatile boolean thrown = false;

      @Override
      public Optional<Object> call(final Method method, final Object[] args) throws VcsException {
        if (!thrown) {
          thrown = true;
          throw new GitExecTimeout();
        }
        fail("Should not try to fetch again");
        return null;
      }
    });
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));

    //try to fetch unknown branch, first fetch fails with exec timeout, repo would be remapped
    VcsRootImpl root2 =
      vcsRoot().withAgentGitPath(getGitPath()).withBranch("refs/heads/personal").withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    AgentRunningBuild build = runningBuild()
      .useLocalMirrors(true)
      .sharedConfigParams("teamcity.git.fetchMirrorRetryTimeouts", "0,0")
      .withAgentConfiguration(myBuilder.getAgentConfiguration())
      .build();

    try {
      myVcsSupport.updateSources(root2, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);
      fail("GitExecTimeout exception expected");
    } catch (GitExecTimeout ignored) {
    }

    //try again, should succeed without remapping, means previous code has not changed mirror directory
    loggingFactory.addCallback(FetchCommand.class.getName() + ".call", (method, args) -> null);
    myVcsSupport.updateSources(root2, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);

    File mirrorAfterBuild = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));
    then(mirrorAfterBuild).isEqualTo(mirror);//repository was not remapped to another dir
  }


  //we run ls-remote during outdated refs cleanup which is needed to
  //successfully checkout when ref a/b is renamed to A/b on win or mac (TW-28735).
  //If we continue silently this can cause performance problems (TW-44944)
  @TestFor(issues = "TW-44944")
  public void should_fail_when_ls_remote_fails() throws Exception {
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).build();

    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    //run build to prepare working dir
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, createRunningBuild(false), false);

    loggingFactory.addCallback(LsRemoteCommand.class.getName() + ".call", new GitCommandProxyCallback() {
      @Override
      public Optional<Object> call(final Method method, final Object[] args) throws VcsException {
        throw new VcsException("TEST ERROR");
      }
    });

    //ls-remote will fail during this build
    try {
      myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, createRunningBuild(false), false);
      fail("should fail");
    } catch (VcsException e) {
      assertTrue(true);
    }
  }


  public void do_not_delete_mirror_if_remote_ref_not_found() throws Exception {
    MockFS fs = new MockFS();
    myVcsSupport = myBuilder.setFS(fs).build();

    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(myMainRepo));
    fs.makeDeleteFail(mirror);//if plugin will remove mirror it will fail and try to remap
    myRoot = vcsRoot().withBranch("refs/heads/unknown").withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).build();
    try {
      String unknownRevision = "abababababababababababababababababababab";
      myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, unknownRevision, myCheckoutDir, createRunningBuild(true), false);
      fail("update on unknown branch should fail");
    } catch (VcsException e) {
      File mirrorAfterFailure = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(myMainRepo));
      then(mirrorAfterFailure).isEqualTo(mirror);//failure should not cause delete or remap
    }
  }


  public void do_not_delete_mirror_on_timeout() throws Exception {
    MockFS fs = new MockFS();
    myVcsSupport = myBuilder.setFS(fs).build();

    String unreachableRepository = "git://some.org/unreachable.git";
    File mirror = myBuilder.getMirrorManager().getMirrorDir(unreachableRepository);
    fs.makeDeleteFail(mirror);//if plugin will remove mirror it will fail and try to remap
    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(unreachableRepository).build();
    try {
      String revision = "abababababababababababababababababababab";
      AgentRunningBuild build = runningBuild().useLocalMirrors(true).sharedConfigParams("teamcity.git.idle.timeout.seconds", "1")
        .withAgentConfiguration(myBuilder.getAgentConfiguration()).build();
      myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, revision, myCheckoutDir, build, false);
      fail("update on unreachable repository should fail");
    } catch (VcsException e) {
      if (e instanceof GitExecTimeout) {
        File mirrorAfterFailure = myBuilder.getMirrorManager().getMirrorDir(unreachableRepository);
        then(mirrorAfterFailure)
          .overridingErrorMessage("Mirror changed after error " + e.toString())
          .isEqualTo(mirror);//failure should not cause delete or remap
      } else {
        //on some platforms fetch from unknown host doesn't result in timeout error
        throw new SkipException("Not a timeout error: " + e.toString());
      }
    }
  }


  @TestFor(issues = "TW-43884")
  public void mirror_delete_can_be_disabled() throws Exception {
    MockFS fs = new MockFS();
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).setFS(fs).build();

    File repo = dataFile("repo_for_fetch.1");
    File remoteRepo = myTempFiles.createTempDir();
    copyRepository(repo, remoteRepo);

    //run build to prepare mirror
    VcsRootImpl root = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "add81050184d3c818560bdd8839f50024c188586", myCheckoutDir, createRunningBuild(true), false);

    //update remote repo: add personal branch
    delete(remoteRepo);
    File updatedRepo = dataFile("repo_for_fetch.2.personal");
    copyRepository(updatedRepo, remoteRepo);


    //create refs/heads/personal/1 so that incremental fetch will fail
    AtomicInteger invocationCount = new AtomicInteger(0);
    loggingFactory.addCallback(FetchCommand.class.getName() + ".call", new GitCommandProxyCallback() {
      @Override
      public Optional<Object> call(final Method method, final Object[] args) throws VcsException {
        if (invocationCount.getAndIncrement() <= 1)
          throw new VcsException("TEST ERROR");
        return null;
      }
    });
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));

    VcsRootImpl root2 = vcsRoot().withAgentGitPath(getGitPath()).withBranch("refs/heads/personal").withFetchUrl(GitUtils.toURL(remoteRepo)).build();
    fs.makeDeleteFail(mirror);
    try {
      AgentRunningBuild build = runningBuild().useLocalMirrors(true)
        .sharedConfigParams(AgentRuntimeProperties.FAIL_ON_CLEAN_CHECKOUT, "true")
        .withAgentConfiguration(myBuilder.getAgentConfiguration())
        .sharedConfigParams("teamcity.git.fetchMirrorRetryTimeouts", "0")
        .build();
      myVcsSupport.updateSources(root2, CheckoutRules.DEFAULT, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", myCheckoutDir, build, false);
      fail("Should fail");
    } catch (VcsException e) {
      File mirrorAfterBuild = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(remoteRepo));
      then(mirrorAfterBuild).isEqualTo(mirror);//should fail on first fetch attempt and not remap or delete the mirror
    }
  }


  public void checkout_tag() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Ref tagRef = r.getRef("refs/tags/v1.0");
    assertNotNull(tagRef);
  }


  @TestFor(issues = "TW-38247")
  public void checkout_revision_reachable_from_tag() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    String revisionInBuild = "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6";
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, revisionInBuild, myCheckoutDir, myBuild, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    String workingDirRevision = r.getAllRefs().get("HEAD").getObjectId().name();
    assertEquals("Wrong revision on agent", revisionInBuild, workingDirRevision);
  }


  public void do_not_create_branch_when_checkout_tag() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Map<String, Ref> refs = r.getRefDatabase().getRefs("refs/");
    assertTrue(refs.containsKey("tags/v1.0"));
    assertTrue(refs.containsKey("tags/v0.7"));//it is reachable from refs/tags/v1.0
    assertTrue(refs.containsKey("tags/v0.5"));//also reachable
    assertEquals(3, refs.size());
  }


  @TestFor(issues = "TW-23707")
  public void do_not_create_remote_branch_unexisting_in_remote_repository() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/changes/1/1");
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "5711cbfe566b6c92e331f95d4b236483f4532eed", myCheckoutDir, myBuild, false);
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertEquals("5711cbfe566b6c92e331f95d4b236483f4532eed", r.getBranch());//checkout a detached commit
    Map<String, Ref> refs = r.getAllRefs();
    assertFalse(refs.containsKey("refs/heads/refs/changes/1/1"));
    assertFalse(refs.containsKey("refs/remotes/origin/changes/1/1"));
  }


  public void checkout_tag_after_branch() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "sub-submodule");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""),
                               GitUtils.makeVersion("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", 1289483394000L), myCheckoutDir, myBuild,
                               false);
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Ref headRef = r.getRef("HEAD");
    assertEquals("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", headRef.getObjectId().name());
  }


  public void should_checkout_tags_reachable_from_branch() throws Exception {
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);
    assertTagExists("refs/tags/v0.5");
    assertTagExists("refs/tags/v1.0");
  }


  @Test(dataProvider = "mirrors")
  public void deleted_tag_in_remote_repository_should_be_deleted_in_local_repository(Boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(useMirrors);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);
    removeTag(myMainRepo, "refs/tags/v0.5");

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);
    assertNoTagExist("refs/tags/v0.5");
  }


  @Test(dataProvider = "mirrors")
  public void updated_tag_in_remote_repository_should_be_updated_in_local_repository(Boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(useMirrors);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);

    final String newCommit = "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6";
    updateRef(myMainRepo, "refs/tags/v1.0", newCommit);

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);
    assertTagExists("refs/tags/v1.0");
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Ref tag = r.getRef("refs/tags/v1.0");
    assertEquals("Local tag is not updated", newCommit, tag.getObjectId().name());
  }


  @TestFor(issues = "TW-47805")
  public void no_redundant_fetches_for_pull_requests() throws Exception {
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    myVcsSupport = myBuilder.setGitMetaFactory(loggingFactory).setFS(new MockFS()).build();

    //create pull-request in remote repo
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/changes/1/1");
    updateRef(myMainRepo, "refs/pull/1/head", "5711cbfe566b6c92e331f95d4b236483f4532eed");

    //run build on pull-request
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "5711cbfe566b6c92e331f95d4b236483f4532eed", myCheckoutDir, myBuild, false);

    //run build again
    int fetchesBefore = loggingFactory.getNumberOfCalls(FetchCommand.class);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "5711cbfe566b6c92e331f95d4b236483f4532eed", myCheckoutDir, myBuild, false);

    //there should be no fetches in the second build
    int redundantFetches = loggingFactory.getNumberOfCalls(FetchCommand.class) - fetchesBefore;
    then(redundantFetches).isEqualTo(0);
  }


  @Test(dataProvider = "mirrors")
  public void fetch_all_heads(boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors),
                                                     PluginConfigImpl.FETCH_ALL_HEADS, "true"));

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    Repository remoteRepo = new RepositoryBuilder().setBare().setGitDir(myMainRepo).build();
    Set<String> remoteHeads = remoteRepo.getRefDatabase().getRefs("refs/heads/").keySet();

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    then(r.getRefDatabase().getRefs("refs/remotes/origin/").keySet()).containsAll(remoteHeads);
  }


  @TestFor(issues = "TW-50714")
  @Test(dataProvider = "mirrors")
  public void fetch_all_heads__non_head_ref(boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.FETCH_ALL_HEADS, "true"));

    myRoot = vcsRoot().withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).withUseMirrors(useMirrors).withBranch("refs/pull/1").build();

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "b896070465af79121c9a4eb5300ecff29453c164", myCheckoutDir, build, false);
  }


  @Test(dataProvider = "mirrors")
  public void fetch_all_heads_before_build_branch(boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors),
                                                     PluginConfigImpl.FETCH_ALL_HEADS, "beforeBuildBranch"));

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    Repository remoteRepo = new RepositoryBuilder().setBare().setGitDir(myMainRepo).build();
    Set<String> remoteHeads = remoteRepo.getRefDatabase().getRefs("refs/heads/").keySet();

    //local repo should contain all heads since build's commit wasn't on the agent
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    then(r.getRefDatabase().getRefs("refs/remotes/origin/").keySet()).containsAll(remoteHeads);
  }


  @Test(dataProvider = "mirrors")
  public void fetch_all_heads_before_build_branch_commit_found(boolean useMirrors) throws Exception {
    //run build to make sure commit is on the agent
    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors)));
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    //run build with fetch_all_head before build's branch
    build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors),
                                   PluginConfigImpl.FETCH_ALL_HEADS, "beforeBuildBranch"));
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    //local repo shouldn't contain all heads since build commit was already on the agent and no fetch is required
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    then(r.getRefDatabase().getRefs("refs/remotes/origin/").keySet()).containsOnly("master");
  }


  private void removeTag(@NotNull File dotGitDir, @NotNull String tagName) {
    delete(tagFile(dotGitDir, tagName));
  }

  private void updateRef(@NotNull File dotGitDir, @NotNull String refName, @NotNull String commit) throws IOException {
    File tagFile = tagFile(dotGitDir, refName);
    FileUtil.writeToFile(tagFile, commit.getBytes());
  }

  private File tagFile(@NotNull File dotGitDir, @NotNull String tagName) {
    return new File(dotGitDir, tagName.replaceAll("/", Matcher.quoteReplacement(File.separator)));
  }

  private void assertNoTagExist(String tag) throws IOException {
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertNull("tag \'" + tag + "\' exists", r.getRef(tag));
  }

  private void assertTagExists(String tag) throws IOException {
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertNotNull("tag \'" + tag + "\' doesn't exist", r.getRef(tag));
  }

  @DataProvider(name = "mirrors")
  public Object[][] mirrors() {
    return new Object[][] {{true}, {false}};
  }

  @Test(dataProvider = "mirrors")
  public void should_do_fetch_if_ref_is_outdated(Boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(useMirrors);
    final File remote = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.2.personal"), remote);
    VcsRootImpl masterRoot = createRoot(remote, "master");
    VcsRootImpl personalRoot = createRoot(remote, "personal");

    myVcsSupport.updateSources(personalRoot, new CheckoutRules(""), "d47dda159b27b9a8c4cee4ce98e4435eb5b17168@1303829462000", myCheckoutDir, build, false);
    myVcsSupport.updateSources(masterRoot,   new CheckoutRules(""), "add81050184d3c818560bdd8839f50024c188586@1303829295000", myCheckoutDir, build, false);

    FileUtil.delete(remote);
    copyRepository(dataFile("repo_for_fetch.2"), remote);

    myVcsSupport.updateSources(masterRoot, new CheckoutRules(""), "d47dda159b27b9a8c4cee4ce98e4435eb5b17168@1303829462000", myCheckoutDir,
                               build, false);
  }


  @Test(dataProvider = "mirrors")
  public void test_update_on_revision_from_feature_branch(Boolean useMirrors) throws Exception {
    AgentRunningBuild build = createRunningBuild(useMirrors);
    final File remote = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.2.personal"), remote);
    VcsRootImpl root = createRoot(remote, "master");
    String commitFromFeatureBranch = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, commitFromFeatureBranch, myCheckoutDir, build, false);
  }


  @Test(dataProvider = "mirrors")
  public void should_use_branch_specified_in_build_parameter(Boolean useMirrors) throws Exception {
    final File remote = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.2.personal"), remote);
    VcsRootImpl root = createRoot(remote, "master");

    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors)));
    String commitFromFeatureBranch = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, commitFromFeatureBranch, myCheckoutDir, build, false);
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertEquals("master", r.getBranch());

    build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, String.valueOf(useMirrors),
                                   GitUtils.getGitRootBranchParamName(root), "refs/heads/personal"));
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, commitFromFeatureBranch, myCheckoutDir, build, false);
    r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertEquals("personal", r.getBranch());
  }


  @Test
  public void test_shallow_clone() throws Exception {
    AgentRunningBuild build = createRunningBuild(new HashMap<String, String>() {{
      put(PluginConfigImpl.USE_MIRRORS, "true");
      put(PluginConfigImpl.USE_SHALLOW_CLONE, "true");
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);
  }


  @TestFor(issues = "TW-27677")
  public void shallow_clone_in_non_master_branch() throws Exception {
    AgentRunningBuild build = createRunningBuild(new HashMap<String, String>() {{
      put(PluginConfigImpl.USE_MIRRORS, "true");
      put(PluginConfigImpl.USE_SHALLOW_CLONE, "true");
      put(GitUtils.getGitRootBranchParamName(myRoot), "refs/heads/version-test");//build on non-master branch
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build, false);
  }


  @TestFor(issues = "TW-37122")
  public void shallow_clone_on_tag() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    AgentRunningBuild build = createRunningBuild(new HashMap<String, String>() {{
      put(PluginConfigImpl.USE_MIRRORS, "true");
      put(PluginConfigImpl.USE_SHALLOW_CLONE, "true");
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);
  }


  @TestFor(issues = "TW-20165")
  public void push_with_local_mirrors_should_go_to_original_repository() throws Exception {
    AgentRunningBuild build = createRunningBuild(true);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitUtils.makeVersion("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", 1289483394000L), myCheckoutDir, build, false);

    final File fileToChange = new File(myCheckoutDir, "file");
    FileUtil.writeToFile(fileToChange, "text".getBytes());

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Git git = new Git(r);
    git.add().addFilepattern("file").call();
    RevCommit commitDuringTheBuild = git.commit().setMessage("Commit during the build").call();
    new PushCommand().run(getGitPath(), myCheckoutDir.getAbsolutePath());//push using native git, seems like jgit doesn't respect url.insteadOf settings

    Repository remote = new RepositoryBuilder().setGitDir(myMainRepo).build();
    assertTrue("Push didn't go to the remote repository", remote.hasObject(commitDuringTheBuild));
  }


  @TestFor(issues = "TW-28735")
  public void fetch_branch_with_same_name_but_different_register() throws Exception {
    AgentRunningBuild buildWithMirrorsEnabled = createRunningBuild(true);
    myRoot = vcsRoot().withBranch("refs/heads/master").withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, buildWithMirrorsEnabled, false);

    //rename master->Master
    Repository r = new RepositoryBuilder().setGitDir(myMainRepo).build();
    r.renameRef("refs/heads/master", "refs/heads/Master").rename();

    myRoot = vcsRoot().withBranch("refs/heads/Master").withAgentGitPath(getGitPath()).withFetchUrl(GitUtils.toURL(myMainRepo)).build();
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, buildWithMirrorsEnabled, false);
  }


  @TestFor(issues = "TW-46266")
  public void should_not_use_custom_clone_on_server() throws Exception {
    File serverCustomCloneDir = myTempFiles.createTempDir();
    VcsRootImpl root = vcsRoot()
      .withAgentGitPath(getGitPath())
      .withFetchUrl(GitUtils.toURL(myMainRepo))
      .withRepositoryPathOnServer(serverCustomCloneDir.getCanonicalPath())
      .build();

    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, createRunningBuild(true), false);

    then(serverCustomCloneDir.listFiles()).isEmpty();
  }


  @TestFor(issues = "TW-44844")
  @Test(dataProviderClass = BaseRemoteRepositoryTest.class, dataProvider = "true,false")
  public void handle_files_marked_as_unchanged(boolean switchBranch) throws Exception {
    //checkout
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "97442a720324a0bd092fb9235f72246dc8b345bc", myCheckoutDir, myBuild, false);

    //modify file
    File f = new File(myCheckoutDir, "dir/a.txt");
    writeFileAndReportErrors(f, "update by build script");

    //git update-index --no-assume-unchanged <file>
    Process updateIndex = new ProcessBuilder().directory(myCheckoutDir).command(getGitPath(), "update-index", "--assume-unchanged", "dir/a.txt").start();
    updateIndex.waitFor();
    if (updateIndex.exitValue() != 0) {
      fail("git update-index failed, exit code " + updateIndex.exitValue() +
           "\nstdout: " + StreamUtil.readText(updateIndex.getInputStream()) +
           "\nstderr: " + StreamUtil.readText(updateIndex.getErrorStream()));
    }

    //update to commit which changes the file
    if (switchBranch)
      myBuild = createRunningBuild(map(GitUtils.getGitRootBranchParamName(myRoot), "refs/heads/personal-branch1"));
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", myCheckoutDir, myBuild, false);
    then(FileUtil.readFile(f)).doesNotContain("update by build script");
  }


  @TestFor(issues = "TW-40313")
  public void should_remove_orphaned_indexes() throws Exception {
    //checkout
    VcsRootImpl root = vcsRoot()
      .withAgentGitPath(getGitPath())
      .withFetchUrl(GitUtils.toURL(myMainRepo))
      .build();

    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, createRunningBuild(true), false);

    //create orphaned idx files
    File mirror = myBuilder.getMirrorManager().getMirrorDir(GitUtils.toURL(myMainRepo));
    File idxInMirror = new File(new File(new File(mirror, "objects"), "pack"), "whatever.idx");
    FileUtil.writeFileAndReportErrors(idxInMirror, "whatever");
    File idxInCheckoutDir = new File(new File(new File(mirror, "objects"), "pack"), "whatever.idx");
    FileUtil.writeFileAndReportErrors(idxInCheckoutDir, "whatever");

    //checkout again
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, createRunningBuild(true), false);

    //orphaned idx files are removed
    then(idxInCheckoutDir).doesNotExist();
    then(idxInMirror).doesNotExist();
  }


  private VcsRootImpl createRoot(final File remote, final String branch) throws IOException {
    myVcsRootId++;
    return new VcsRootImpl(myVcsRootId, new HashMap<String, String>() {{
      put(VcsRootImpl.VCS_NAME_PROP, Constants.VCS_NAME);
      put(VcsRootImpl.VCS_ROOT_NAME_PROP, "test" + myVcsRootId);
      put(Constants.FETCH_URL, GitUtils.toURL(remote));
      put(Constants.AGENT_GIT_PATH, getGitPath());
      put(Constants.BRANCH_NAME, branch);
    }});
  }


  private void testSubSubmoduleCheckout(boolean recursiveSubmoduleCheckout) throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "sub-submodule");
    if (recursiveSubmoduleCheckout) {
      myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    } else {
      myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT.name());
    }

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION,
                               myCheckoutDir, myBuild, false);

    assertTrue(new File(myCheckoutDir, "first-level-submodule" + File.separator + "submoduleFile.txt").exists());
    if (recursiveSubmoduleCheckout) {
      assertTrue(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "file.txt").exists());
      assertTrue(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "new file.txt").exists());
    } else {
      assertFalse(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "file.txt").exists());
      assertFalse(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "new file.txt").exists());
    }
  }


  private AgentRunningBuild createRunningBuild(boolean useLocalMirrors) {
    return runningBuild().useLocalMirrors(useLocalMirrors).withAgentConfiguration(myBuilder.getAgentConfiguration()).build();
  }


  private AgentRunningBuild createRunningBuild(final Map<String, String> sharedConfigParameters) {
    return runningBuild().sharedConfigParams(sharedConfigParameters).withAgentConfiguration(myBuilder.getAgentConfiguration()).build();
  }


  private void copyRepository(File src, File dst) throws IOException {
    copyDir(src, dst);
    new File(dst, "refs" + File.separator + "heads").mkdirs();
  }


  private class PushCommand {
    void run(String gitPath, String workDirectory) throws Exception {
      File tmpDir = new File(FileUtil.getTempDirectory());
      GitCommandLine cmd = new GitCommandLine(null, SystemInfo.isUnix ? new UnixScriptGen(tmpDir, new EscapeEchoArgumentUnix())
                                                                      : new WinScriptGen(tmpDir, new EscapeEchoArgumentWin()),
                                              tmpDir,
                                              true,
                                              GitProgressLogger.NO_OP,
                                              GitVersion.MIN,
                                              new HashMap<String, String>(),
                                              new NoBuildContext());
      cmd.setExePath(gitPath);
      cmd.setWorkingDirectory(new File(workDirectory));
      cmd.addParameters("push", "origin", "master");
      CommandUtil.runCommand(cmd);
    }
  }
}
