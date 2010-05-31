/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.agent.parameters.AgentParameterResolverFactory;
import jetbrains.buildServer.agent.parameters.ParameterResolverAgentProvider;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentSubmodulesTest extends BaseTestCase {

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
   * Directory where we clone main repository
   */
  private File myCheckoutDir;

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

    myCheckoutDir = myTempFiles.createTempDir();
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    FileUtil.delete(myMainRepo);
    FileUtil.delete(mySubmoduleRepo);
    FileUtil.delete(myCheckoutDir);
  }

  /**
   * Test checkout submodules on agent. Machine that runs this test should have git installed. 
   * @throws VcsException
   * @throws IOException
   */
  public void testSubmodulesCheckout() throws VcsException, IOException {
    File agentConfigurationTempDirectory = myTempFiles.createTempDir();
    Mock buildAgentConfigurationMock = new Mock(BuildAgentConfiguration.class);
    buildAgentConfigurationMock.stubs().method("getAgentParameters").will(returnValue(new HashMap<String, String>()));
    buildAgentConfigurationMock.stubs().method("getCustomProperties").will(returnValue(new HashMap<String, String>()));
    buildAgentConfigurationMock.stubs().method("getOwnPort").will(returnValue(600));
    buildAgentConfigurationMock.stubs().method("getTempDirectory").will(returnValue(agentConfigurationTempDirectory));

    Mock extensionHolderMock = new Mock(ExtensionHolder.class);
    extensionHolderMock.stubs().method("getExtensions").withAnyArguments().will(returnValue(new ArrayList<ParameterResolverAgentProvider>()));
    Mock currentBuildTrackerMock = new Mock(CurrentBuildTracker.class);

    Mock agentMock = new Mock(BuildAgent.class);
    Mock xmlRpcHandlerManagerMock = new Mock(XmlRpcHandlerManager.class);
    xmlRpcHandlerManagerMock.stubs().method("addHandler").withAnyArguments();
    agentMock.stubs().method("getXmlRpcHandlerManager").will(returnValue(xmlRpcHandlerManagerMock.proxy()));

    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(myMainRepo));

    root.addProperty(Constants.AGENT_GIT_PATH, getGitPath());
    root.addProperty(Constants.BRANCH_NAME, "patch-tests");
    root.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());

    FileUtil.delete(myCheckoutDir);

    new GitAgentVcsSupport((BuildAgentConfiguration) buildAgentConfigurationMock.proxy(),
                           new SmartDirectoryCleaner() {
                             public void cleanFolder(@NotNull File file, @NotNull SmartDirectoryCleanerCallback callback) {/* do nothing*/}
                           },
                           new GitAgentSSHService((BuildAgent) agentMock.proxy(), (BuildAgentConfiguration) buildAgentConfigurationMock.proxy()),
                           new AgentParameterResolverFactory((ExtensionHolder) extensionHolderMock.proxy()),
                           (CurrentBuildTracker) currentBuildTrackerMock.proxy())
      .updateSources(root,
                     new CheckoutRules(""),
                     GitVcsSupportTest.SUBMODULE_ADDED_VERSION,
                     myCheckoutDir,
                     new MyLogger());

    assertTrue(new File (myCheckoutDir, "submodule" + File.separator + "file.txt").exists());
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

  private final static class MyLogger implements BuildProgressLogger {
    public void activityStarted(String activityName, String activityType) {}
    public void activityFinished(String activityName, String activityType) {}
    public void targetStarted(String targetName) {}
    public void targetFinished(String targetName) {}
    public void buildFailureDescription(String message) {}
    public void progressStarted(String message) {}
    public void progressFinished() {}
    public void logMessage(BuildMessage1 message) {}
    public void flush() {}
    public void flowStarted(String flowId, String parentFlowId) {}
    public void logTestStarted(String name) {}
    public void message(String message) {}
    public void logTestStarted(String name, Date timestamp) {}
    public void error(String message) {}
    public void flowFinished(String flowId) {}
    public void warning(String message) {}
    public void logTestFinished(String name) {}
    public void exception(Throwable th) {}
    public void logTestFinished(String name, Date timestamp) {}
    public void progressMessage(String message) {}
    public void logTestIgnored(String name, String reason) {}
    public void logSuiteStarted(String name) {}
    public void logSuiteStarted(String name, Date timestamp) {}
    public void logSuiteFinished(String name) {}
    public void logSuiteFinished(String name, Date timestamp) {}
    public void logTestStdOut(String testName, String out) {}
    public void logTestStdErr(String testName, String out) {}
    public void logTestFailed(String testName, Throwable e) {}
    public void logComparisonFailure(String testName, Throwable e, String expected, String actual) {}
    public void logTestFailed(String testName, String message, String stackTrace) {}
    public void ignoreServiceMessages(java.lang.Runnable r) {}
    public void error(String s, String s1) {}
  }

}
