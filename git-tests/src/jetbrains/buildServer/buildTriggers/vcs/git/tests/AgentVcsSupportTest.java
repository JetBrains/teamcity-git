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

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.*;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitPathResolver;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentVcsSupportTest extends BaseTestCase {

  /**
   * Temporary files
   */
  protected static TempFiles myTempFiles = new TempFiles();
  /**
   * Main repository dir
   */
  private File myMainRepo;
  /**
   * Submodules repository dir
   */
  private File mySubmoduleRepo;
  /**
   * Another submodule repository dir
   */
  private File mySubmoduleRepo2;
  /**
   * Directory where we clone main repository
   */
  private File myCheckoutDir;

  /**
   * Agent temp directory
   */
  private File agentConfigurationTempDirectory;

  /**
   * VcsRoot for tests
   */
  private VcsRootImpl myRoot;

  private Mockery myMockery;

  /**
   * Mocks of objects provided by TeamCity server
   */
  private GitAgentVcsSupport myVcsSupport;
  private BuildProgressLogger myLogger;
  private AgentRunningBuild myBuild;


  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        myTempFiles.cleanup();
      }
    }));
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    File masterRep = dataFile("repo.git");
    myMainRepo = myTempFiles.createTempDir();
    copyDir(masterRep, myMainRepo);
    new File(myMainRepo, "refs" + File.separator + "heads").mkdirs();

    File submoduleRep = dataFile("submodule.git");
    mySubmoduleRepo = new File(myMainRepo.getParentFile(), "submodule.git");
    delete(mySubmoduleRepo);
    copyDir(submoduleRep, mySubmoduleRepo);
    new File(mySubmoduleRepo, "refs" + File.separator + "heads").mkdirs();

    File submoduleRep2 = dataFile("sub-submodule.git");
    mySubmoduleRepo2 = new File(myMainRepo.getParentFile(), "sub-submodule.git");
    delete(mySubmoduleRepo2);
    copyDir(submoduleRep2, mySubmoduleRepo2);
    new File(mySubmoduleRepo2, "refs" + File.separator + "heads").mkdirs();

    myCheckoutDir = myTempFiles.createTempDir();

    agentConfigurationTempDirectory = myTempFiles.createTempDir();

    myMockery = new Mockery();

    final GitPathResolver resolver = myMockery.mock(GitPathResolver.class);
    final String pathToGit = getGitPath();

    myMockery.checking(new Expectations() {{
      allowing(resolver).resolveGitPath(with(any(BuildAgentConfiguration.class)), with(any(String.class))); will(returnValue(pathToGit));
    }});
    BuildAgentConfiguration configuration = createBuildAgentConfiguration();
    myVcsSupport = new GitAgentVcsSupport(configuration,
                                          createSmartDirectoryCleaner(),
                                          new GitAgentSSHService(createBuildAgent(), configuration, new PluginDescriptor() {
                                            @NotNull
                                            public File getPluginRoot() {
                                              return new File("jetbrains.git");
                                            }
                                          }),
                                          resolver);

    myLogger = createLogger();
    myBuild = createRunningBuild();

    myRoot = new VcsRootImpl(1, Constants.VCS_NAME);
    myRoot.addProperty(Constants.FETCH_URL, GitUtils.toURL(myMainRepo));
    myRoot.addProperty(Constants.AGENT_GIT_PATH, pathToGit);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    FileUtil.delete(myMainRepo);
    FileUtil.delete(mySubmoduleRepo);
    FileUtil.delete(mySubmoduleRepo2);
    FileUtil.delete(myCheckoutDir);
    FileUtil.delete(agentConfigurationTempDirectory);
  }

  /**
   * Test work normally if .git/index.lock file exists
   * @throws VcsException
   * @throws IOException
   */
  public void testRecoverIndexLock() throws VcsException, IOException {
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
   * @throws VcsException
   * @throws IOException
   */
  public void testRecoverRefLock() throws VcsException, IOException {
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
                                                         GitUtils.branchRef("patch-tests") +
                                                         ".lock"));
    
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), firstCommitInPatchTests, myCheckoutDir, myBuild, false);
  }


  /**
   * Test checkout submodules on agent. Machine that runs this test should have git installed. 
   * @throws VcsException
   * @throws IOException
   */
  public void testSubmodulesCheckout() throws VcsException, IOException {
    myRoot.addProperty(Constants.BRANCH_NAME, "patch-tests");
    myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());

    myVcsSupport.updateSources(myRoot, new CheckoutRules(""), GitVcsSupportTest.SUBMODULE_ADDED_VERSION,
                               myCheckoutDir, myBuild, false);

    assertTrue(new File (myCheckoutDir, "submodule" + File.separator + "file.txt").exists());
  }


  /**
   * Test non-recursive submodules checkout: submodules of submodules are not retrieved
   * @throws VcsException
   * @throws IOException
   */
  public void testSubSubmodulesCheckoutNonRecursive() throws VcsException, IOException {
    testSubSubmoduleCheckout(false);
  }


  /**
   * Test recursive submodules checkout: submodules of submodules are retrieved
   * @throws VcsException
   * @throws IOException
   */
  public void testSubSubmodulesCheckoutRecursive() throws VcsException, IOException {
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


  private void testSubSubmoduleCheckout(boolean recursiveSubmoduleCheckout) throws IOException, VcsException {
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


  private CurrentBuildTracker createCurrentBuildTracker() {
    CurrentBuildTracker tracker = myMockery.mock(CurrentBuildTracker.class);
    return tracker;
  }


  private ExtensionHolder createExtensionHolder() {
    final ExtensionHolder holder = myMockery.mock(ExtensionHolder.class);
    myMockery.checking(new Expectations(){{
      allowing(holder).getExtensions(with(Expectations.<Class<TeamCityExtension>>anything()));
    }});
    return holder;
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
    final SmartDirectoryCleaner cleaner = myMockery.mock(SmartDirectoryCleaner.class);
    myMockery.checking(new Expectations() {{
      allowing(cleaner).cleanFolder(with(any(File.class)), with(any(SmartDirectoryCleanerCallback.class)));
    }});
    return cleaner;
  }

  
  private BuildProgressLogger createLogger() {
    final BuildProgressLogger logger = myMockery.mock(BuildProgressLogger.class);
    myMockery.checking(new Expectations(){{
      allowing(logger).message(with(any(String.class)));
      allowing(logger).warning(with(any(String.class)));
    }});
    return logger;
  }


  private AgentRunningBuild createRunningBuild() {
    final AgentRunningBuild build = myMockery.mock(AgentRunningBuild.class);
    myMockery.checking(new Expectations() {{
      allowing(build).getBuildLogger(); will(returnValue(myLogger));
      allowing(build).getSharedConfigParameters(); will(returnValue(new HashMap<String, String>()));
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
}
