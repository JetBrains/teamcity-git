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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsUtil;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentVcsSupportTest {

  private TempFiles myTempFiles;
  private File myMainRepo;
  private File myCheckoutDir;
  private File agentConfigurationTempDirectory;
  private VcsRootImpl myRoot;
  private BuildAgentConfiguration myAgentConfiguration;
  private Mockery myMockery;
  private int myBuildId = 0;
  private int myVcsRootId = 0;
  private GitAgentVcsSupport myVcsSupport;
  private BuildProgressLogger myLogger;
  private AgentRunningBuild myBuild;
  private PluginConfigFactory myConfigFactory;
  private MirrorManager myMirrorManager;
  private BuildAgent myBuildAgent;

  static {
    Logger.setFactory(new Log4jFactory());
  }

  @BeforeMethod
  public void setUp() throws Exception {
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
    agentConfigurationTempDirectory = myTempFiles.createTempDir();

    myMockery = new Mockery();
    final GitPathResolver resolver = myMockery.mock(GitPathResolver.class);
    final GitDetector detector = new GitDetectorImpl(resolver);
    final String pathToGit = getGitPath();

    myMockery.checking(new Expectations() {{
      allowing(resolver).resolveGitPath(with(any(BuildAgentConfiguration.class)), with(any(String.class))); will(returnValue(pathToGit));
    }});
    myAgentConfiguration = createBuildAgentConfiguration();
    myConfigFactory = new PluginConfigFactoryImpl(myAgentConfiguration, detector);
    myMirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(myAgentConfiguration), new HashCalculatorImpl());
    GitMetaFactory metaFactory = new GitMetaFactoryImpl();
    myBuildAgent = createBuildAgent();
    myVcsSupport = new GitAgentVcsSupport(createSmartDirectoryCleaner(),
                                          new GitAgentSSHService(myBuildAgent, myAgentConfiguration, new GitPluginDescriptor()),
                                          myConfigFactory, myMirrorManager, metaFactory);
    myLogger = createLogger();
    myBuild = createRunningBuild(true);

    myRoot = vcsRoot().withAgentGitPath(pathToGit).withFetchUrl(GitUtils.toURL(myMainRepo)).build();
  }


  @AfterMethod
  protected void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  @TestFor(issues = "TW-33401")
  @Test(dataProvider = "mirrors")
  public void should_not_remove_remote_tracking_branches(Boolean useMirrors) throws VcsException {
    LoggingGitMetaFactory loggingFactory = new LoggingGitMetaFactory();
    GitAgentVcsSupport git = new GitAgentVcsSupport(createSmartDirectoryCleaner(),
                                                    new GitAgentSSHService(myBuildAgent, myAgentConfiguration, new GitPluginDescriptor()),
                                                    myConfigFactory, myMirrorManager, loggingFactory);

    AgentRunningBuild build = createRunningBuild(map(PluginConfigImpl.USE_MIRRORS, useMirrors.toString()));

    git.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    //we already have everything we need for this update, no fetch should be executed
    git.updateSources(myRoot, CheckoutRules.DEFAULT, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", myCheckoutDir, build, false);

    assertFalse("Refs removed: " + loggingFactory.getInvokedMethods(UpdateRefCommand.class),
                loggingFactory.getInvokedMethods(UpdateRefCommand.class).contains("delete"));
    assertTrue("Redundant fetch", loggingFactory.getInvokedMethods(FetchCommand.class).isEmpty());
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

    File mirror = myMirrorManager.getMirrorDir(fetchUrl);
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


  /**
   * Test for TW-13009
   */
  public void testWindowsSubmodulePath() {
    final String windowsPathSeparator = "\\";
    try {
      "/".replaceAll("/", windowsPathSeparator);
    } catch (StringIndexOutOfBoundsException e) {
      //this means we should escape windowsPath
    }
    "/".replaceAll("/", Matcher.quoteReplacement(windowsPathSeparator));
  }


  public void should_create_bare_repository_in_caches_dir() throws Exception {
    File mirrorsDir = myAgentConfiguration.getCacheDirectory("git");
    assertTrue(mirrorsDir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        return f.isDirectory();
      }
    }).length == 0);

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    GitVcsRoot root = new GitVcsRoot(mirrorManager, myRoot);
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
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(buildWithMirrorsEnabled, myRoot), new HashCalculatorImpl());
    GitVcsRoot root = new GitVcsRoot(mirrorManager, myRoot);
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
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    AgentRunningBuild build2 = createRunningBuild(false);
    GitVcsRoot root = new GitVcsRoot(mirrorManager, myRoot);
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


  @TestFor(issues = "TW-25839")
  public void update_should_not_fail_if_local_mirror_is_corrupted() throws Exception {
    AgentRunningBuild buildWithMirrorsEnabled = createRunningBuild(true);
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildWithMirrorsEnabled, false);

    //corrupt local mirror
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    GitVcsRoot root = new GitVcsRoot(mirrorManager, myRoot);
    File mirror = mirrorManager.getMirrorDir(root.getRepositoryFetchURL().toString());
    File[] children = mirror.listFiles();
    if (children != null) {
      for (File child : children) {
        delete(child);
      }
    }

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, buildWithMirrorsEnabled, false);
  }


  @TestFor(issues = "TW-29291")
  public void shallow_clone_should_check_if_auxiliary_branch_already_exists() throws Exception {
    AgentRunningBuild build = createRunningBuild(new HashMap<String, String>() {{
      put(PluginConfigImpl.USE_MIRRORS, "true");
      put(PluginConfigImpl.USE_SHALLOW_CLONE, "true");
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build, true);

    //manually create a branch tmp_branch_for_build with, it seems like it wasn't removed due to errors in previous checkouts
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    GitVcsRoot root = new GitVcsRoot(mirrorManager, myRoot);
    File mirror = mirrorManager.getMirrorDir(root.getRepositoryFetchURL().toString());
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
    File mirror = myMirrorManager.getMirrorDir(GitUtils.toURL(remoteRepo));
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


  public void checkout_tag() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "refs/tags/v1.0");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Ref tagRef = r.getRef("refs/tags/v1.0");
    assertNotNull(tagRef);
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
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitUtils.makeVersion("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", 1289483394000L), myCheckoutDir, myBuild, false);
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
    updateTag(myMainRepo, "refs/tags/v1.0", newCommit);

    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build, false);
    assertTagExists("refs/tags/v1.0");
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    Ref tag = r.getRef("refs/tags/v1.0");
    assertEquals("Local tag is not updated", newCommit, tag.getObjectId().name());
  }

  private void removeTag(@NotNull File dotGitDir, @NotNull String tagName) {
    delete(tagFile(dotGitDir, tagName));
  }

  private void updateTag(@NotNull File dotGitDir, @NotNull String tagName, @NotNull String commit) throws IOException {
    File tagFile = tagFile(dotGitDir, tagName);
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
      put(ServerProvidedProperties.TEAMCITY_BUILD_VCS_BRANCH + "." + VcsUtil.getSimplifiedName(myRoot), "refs/heads/version-test");//build on non-master branch
    }});
    myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", myCheckoutDir, build, false);
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

    assertTrue(new File (myCheckoutDir, "first-level-submodule" + File.separator + "submoduleFile.txt").exists());
    if (recursiveSubmoduleCheckout) {
      assertTrue(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "file.txt").exists());
      assertTrue(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "new file.txt").exists());
    } else {
      assertFalse(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "file.txt").exists());
      assertFalse(new File (myCheckoutDir, "first-level-submodule" + File.separator + "sub-sub" + File.separator + "new file.txt").exists());
    }
  }


  private BuildAgent createBuildAgent() {
    final BuildAgent agent = myMockery.mock(BuildAgent.class);
    final XmlRpcHandlerManager manager = myMockery.mock(XmlRpcHandlerManager.class);
    myMockery.checking(new Expectations() {{
      allowing(agent).getXmlRpcHandlerManager();
      will(returnValue(manager));
      ignoring(manager);
    }});
    return agent;
  }


  private BuildAgentConfiguration createBuildAgentConfiguration() throws IOException {
    final File cacheDir = myTempFiles.createTempDir();
    final BuildAgentConfiguration configuration = myMockery.mock(BuildAgentConfiguration.class);
    myMockery.checking(new Expectations() {{
      allowing(configuration).getAgentParameters(); will(returnValue(new HashMap<String, String>()));
      allowing(configuration).getCustomProperties(); will(returnValue(new HashMap<String, String>()));
      allowing(configuration).getOwnPort(); will(returnValue(600));
      allowing(configuration).getTempDirectory(); will(returnValue(agentConfigurationTempDirectory));
      allowing(configuration).getConfigurationParameters(); will(returnValue(new HashMap<String, String>()));
      allowing(configuration).getCacheDirectory("git"); will(returnValue(cacheDir));
    }});
    return configuration;
  }


  private SmartDirectoryCleaner createSmartDirectoryCleaner() {
    return new SmartDirectoryCleaner() {
      public void cleanFolder(@NotNull File file, @NotNull SmartDirectoryCleanerCallback callback) {
        FileUtil.delete(file);
      }
    };
  }


  private BuildProgressLogger createLogger() {
    final BuildProgressLogger logger = myMockery.mock(BuildProgressLogger.class);
    myMockery.checking(new Expectations() {{
      ignoring(logger);
    }});
    return logger;
  }


  private AgentRunningBuild createRunningBuild(boolean useLocalMirrors) {
    final Map<String, String> sharedConfigParameters = new HashMap<String, String>();
    sharedConfigParameters.put(PluginConfigImpl.USE_MIRRORS, String.valueOf(useLocalMirrors));
    return createRunningBuild(sharedConfigParameters);
  }


  private AgentRunningBuild createRunningBuild(final Map<String, String> sharedConfigParameters) {
    final AgentRunningBuild build = myMockery.mock(AgentRunningBuild.class, "build" + myBuildId++);
    myMockery.checking(new Expectations() {{
      allowing(build).getBuildLogger(); will(returnValue(myLogger));
      allowing(build).getSharedConfigParameters(); will(returnValue(sharedConfigParameters));
      allowing(build).getBuildTempDirectory(); will(returnValue(new File(FileUtil.getTempDirectory())));
    }});
    return build;
  }


  /**
   * Get path to git executable.
   * @return return value of environment variable TEAMCITY_GIT_PATH, or "git" if variable is not set.
   * @throws IOException
   */
  private String getGitPath() throws IOException {
    String providedGit = System.getenv(Constants.TEAMCITY_AGENT_GIT_PATH);
    if (providedGit != null) {
      return providedGit;
    } else {
      return "git";
    }
  }


  private void copyRepository(File src, File dst) throws IOException {
    copyDir(src, dst);
    new File(dst, "refs" + File.separator + "heads").mkdirs();
  }


  private class GitPluginDescriptor implements PluginDescriptor {
    @NotNull
    public File getPluginRoot() {
      return new File("jetbrains.git");
    }
  }


  private class PushCommand {
    void run(String gitPath, String workDirectory) throws Exception {
      GitCommandLine cmd = new GitCommandLine(null, SystemInfo.isUnix ? new UnixAskPassGen(new EscapeEchoArgumentUnix()) : new WinAskPassGen(new EscapeEchoArgumentWin()), true);
      cmd.setExePath(gitPath);
      cmd.setWorkingDirectory(new File(workDirectory));
      cmd.addParameters("push", "origin", "master");
      CommandUtil.runCommand(cmd);
    }
  }
}
