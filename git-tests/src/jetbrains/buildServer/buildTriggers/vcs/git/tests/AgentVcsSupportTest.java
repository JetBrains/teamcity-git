/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
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
import java.util.regex.Pattern;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.util.FileUtil.writeFile;
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
    myVcsSupport = new GitAgentVcsSupport(createSmartDirectoryCleaner(), new GitAgentSSHService(createBuildAgent(), myAgentConfiguration, new GitPluginDescriptor()), myConfigFactory, new HashCalculatorImpl());
    myLogger = createLogger();
    myBuild = createRunningBuild(true);


    myRoot = new VcsRootImpl(1, new HashMap<String, String>() {{
      put(VcsRootImpl.VCS_NAME_PROP, Constants.VCS_NAME);
      put(VcsRootImpl.VCS_ROOT_NAME_PROP, "test");
      put(Constants.FETCH_URL, GitUtils.toURL(myMainRepo));
      put(Constants.AGENT_GIT_PATH, pathToGit);
    }});
  }


  @AfterMethod
  protected void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  /**
   * Test work normally if .git/index.lock file exists
   */
  public void testRecoverIndexLock() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "master");

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
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    String firstCommitInPatchTests = GitUtils.makeVersion("a894d7d58ffde625019a9ecf8267f5f1d1e5c341", 1245766034000L);
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);

    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    //now we have 2 branches in local repository

    //emulate incorrect git termination (in this it could leave refs/heads/<branch-name>.lock file)
    FileUtil.createIfDoesntExist(new File(myCheckoutDir, ".git" + File.separator +
                                                         GitUtils.expandRef("patch-tests") +
                                                         ".lock"));

    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);
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

    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    Settings settings = new Settings(mirrorManager, myRoot);
    File bareRepositoryDir = settings.getRepositoryDir();
    assertTrue(bareRepositoryDir.exists());
    //check some dirs that should be present in the bare repository:
    File objectsDir = new File(bareRepositoryDir, "objects");
    assertTrue(new File(bareRepositoryDir, "info").exists());
    assertTrue(objectsDir.exists());
    assertTrue(new File(bareRepositoryDir, "refs").exists());

    String config = FileUtil.loadTextAndClose(new FileReader(new File(bareRepositoryDir, "config")));
    assertTrue(config.contains("[remote \"origin\"]"));
    String remoteUrl = "url = " + settings.getRepositoryFetchURL();
    assertTrue(config.contains(remoteUrl));

    File packDir = new File(objectsDir, "pack");
    boolean looseObjectsExists = objectsDir.listFiles().length > 2;//2 - because there are 2 dirs there: info and pack
    boolean packFilesExists = packDir.listFiles().length >=2; //at least one pack file with its index exists
    boolean fetchWasDone = looseObjectsExists || packFilesExists;
    assertTrue(fetchWasDone);
  }


  public void old_cloned_repository_should_use_local_mirror() throws Exception {
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigFactory.createConfig(myBuild, myRoot), new HashCalculatorImpl());
    Settings settings = new Settings(mirrorManager, myRoot);
    File bareRepositoryDir = settings.getRepositoryDir();

    //emulate old cloned repository (it has no config option 'url.<URL>.insteadOf')
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    //cut off 'url.<URL>.insteadOf option:
    File gitConfigFile = new File(myCheckoutDir, ".git" + File.separator + "config");
    String config = FileUtil.loadTextAndClose(new FileReader(gitConfigFile));
    String localMirrorUrl = new URIish(bareRepositoryDir.toURI().toASCIIString()).toString();
    Pattern pattern = Pattern.compile("(.*)\\[url \"" + localMirrorUrl + "\"\\]\\s+insteadOf = " +
                                      Pattern.quote(settings.getRepositoryFetchURL().toString()) +
                                      "\\s*(.*)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(config);
    assertTrue("config is " + config, matcher.matches());
    String newConfig = matcher.group(1) + matcher.group(2);
    writeFile(gitConfigFile, newConfig);

    //at this point cloned repository do not use local mirror, after second call to updateSources it should start using it

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);
    config = FileUtil.loadTextAndClose(new FileReader(gitConfigFile));
    matcher = pattern.matcher(config);
    assertTrue(matcher.matches());
  }


  public void do_not_use_mirror_if_agent_property_set_to_false() throws Exception {
    AgentRunningBuild build2 = createRunningBuild(false);
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);
    File gitConfigFile = new File(myCheckoutDir, ".git" + File.separator + "config");
    String config = FileUtil.loadTextAndClose(new FileReader(gitConfigFile));
    assertFalse(config, config.contains("insteadOf"));
  }


  public void stop_use_mirror_if_agent_property_changed_to_false() throws Exception {
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
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
    Settings settings = new Settings(mirrorManager, myRoot);
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);

    //add some mirror
    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    StoredConfig config = r.getConfig();
    config.setString("url", "/some/path", "insteadOf", settings.getRepositoryFetchURL().toString());
    config.save();

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, build2, false);
    config = new RepositoryBuilder().setWorkTree(myCheckoutDir).build().getConfig();
    assertTrue(config.getSubsections("url").isEmpty());
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
    assertTrue(refs.containsKey("tags/v0.5"));//it is reachable from refs/tags/v1.0
    assertEquals(2, refs.size());
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
    myRoot.addProperty(Constants.BRANCH_NAME, "master");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.VERSION_TEST_HEAD, myCheckoutDir, myBuild, false);

    Repository r = new RepositoryBuilder().setWorkTree(myCheckoutDir).build();
    assertNotNull(r.getRef("refs/tags/v0.5"));
    assertNotNull(r.getRef("refs/tags/v1.0"));
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

    myVcsSupport.updateSources(masterRoot, new CheckoutRules(""), "d47dda159b27b9a8c4cee4ce98e4435eb5b17168@1303829462000", myCheckoutDir, build, false);
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
      allowing(agent).getXmlRpcHandlerManager(); will(returnValue(manager));
      allowing(manager).addHandler(with(any(String.class)), with(any(Object.class)));
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
    myMockery.checking(new Expectations(){{
      allowing(logger).message(with(any(String.class)));
      allowing(logger).warning(with(any(String.class)));
    }});
    return logger;
  }


  private AgentRunningBuild createRunningBuild(boolean useLocalMirrors) {
    final Map<String, String> sharedConfigParameters = new HashMap<String, String>();
    sharedConfigParameters.put("teamcity.git.use.local.mirrors", String.valueOf(useLocalMirrors));
    final AgentRunningBuild build = myMockery.mock(AgentRunningBuild.class, "build"+myBuildId++);
    myMockery.checking(new Expectations() {{
      allowing(build).getBuildLogger(); will(returnValue(myLogger));
      allowing(build).getSharedConfigParameters(); will(returnValue(sharedConfigParameters));
    }});
    return build;
  }


  /**
   * Get path to git executable.
   * @return return value of environment variable TEAMCITY_GIT_PATH, or "git" if variable is not set.
   * @throws IOException
   */
  private String getGitPath() throws IOException {
    String providedGit = System.getenv(Constants.GIT_PATH_ENV);
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
}
