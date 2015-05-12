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

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchProcess;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.patches.PatchTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;

@Test
public class GitPatchTest extends PatchTestCase {

  private File myMainRepositoryDir;
  private PluginConfigBuilder myConfigBuilder;
  private ResetCacheRegister myResetCacheManager;
  private Properties myPropertiesBeforeTest;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};
    Mockery context = new Mockery();
    ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myConfigBuilder = new PluginConfigBuilder(serverPaths);
    File repoGitDir = dataFile("repo.git");
    File tmpDir = myTempFiles.createTempDir();
    myMainRepositoryDir = new File(tmpDir, "repo.git");
    copyRepository(repoGitDir, myMainRepositoryDir);
    copyRepository(dataFile("submodule.git"), new File(tmpDir, "submodule"));
    copyRepository(dataFile("submodule.git"), new File(tmpDir, "submodule.git"));
    copyRepository(dataFile("sub-submodule.git"), new File(tmpDir, "sub-submodule.git"));
    myResetCacheManager = context.mock(ResetCacheRegister.class);
    context.checking(new Expectations() {{
      allowing(myResetCacheManager).registerHandler(with(any(ResetCacheHandler.class)));
    }});
    myPropertiesBeforeTest = GitTestUtil.copyCurrentProperties();
  }


  @Override
  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    GitTestUtil.restoreProperties(myPropertiesBeforeTest);
  }

  @Override
  protected String getTestDataPath() {
    return dataFile().getPath();
  }


  @DataProvider(name = "patchInSeparateProcess")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { Boolean.TRUE },
      new Object[] { Boolean.FALSE }
    };
  }


  @Test(dataProvider = "patchInSeparateProcess")
  public void check_buildPatch_understands_revisions_with_timestamps(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    checkPatch("cleanPatch1", null, GitUtils.makeVersion("a894d7d58ffde625019a9ecf8267f5f1d1e5c341", 1237391915000L));
    checkPatch("patch1",
               GitUtils.makeVersion("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", 1238420977000L),
               GitUtils.makeVersion("0dd03338d20d2e8068fbac9f24899d45d443df38", 1238421020000L));
  }


  @TestFor(issues = "TW-40897")
  public void should_pass_proxy_settings_to_patch_in_separate_process() throws Exception {
    String classpath = myConfigBuilder.build().getPatchClasspath() + File.pathSeparator +
                       ClasspathUtil.composeClasspath(new Class[]{CheckProxyPropertiesPatchBuilder.class}, null, null);
    myConfigBuilder.setSeparateProcessForPatch(true)
      .setPatchClassPath(classpath)
      .setPatchBuilderClassName(CheckProxyPropertiesPatchBuilder.class.getName());
    System.setProperty("http.proxyHost", "httpProxyHost");
    System.setProperty("http.proxyPort", "81");
    System.setProperty("https.proxyHost", "httpsProxyHost");
    System.setProperty("https.proxyPort", "82");
    System.setProperty("http.nonProxyHosts", "some.org");
    System.setProperty("teamcity.git.sshProxyType", "http");
    System.setProperty("teamcity.git.sshProxyHost", "sshProxyHost");
    System.setProperty("teamcity.git.sshProxyPort", "83");
    checkPatch("cleanPatch1", null, "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");
  }


  @Test(dataProvider = "patchInSeparateProcess")
  public void build_patch_several_roots(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);

    //A build configuration can have several VCS roots, TeamCity builds patches in them one by one
    //in unspecified order and then combines them into a single patch. That means a patch for
    //individual VCS root should never delete the root directory, because this action could delete
    //sources of another VCS root. Also patches should not contain an 'EXIT' command, otherwise
    //when agent applies a combined patch it will stop after first 'EXIT'.

    //patch8 is combination of patch1 and patch6
    setName("patch8");
    //patch1
    GitVcsSupport support = getSupport();
    VcsRoot root1 = getRoot("patch-tests", false);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root1, null, "0dd03338d20d2e8068fbac9f24899d45d443df38", builder, CheckoutRules.DEFAULT);

    //patch6
    VcsRoot root2 = getRoot("rename-test", false);
    //pass an unknown fromRevision 'a...a' to ensure we don't remove the root dir if the fromRevision is not found
    support.buildPatch(root2, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "2eed4ae6732536f76a65136a606f635e8ada63b9", builder, CheckoutRules.DEFAULT);
    builder.close();
    checkPatchResult(output.toByteArray());
  }


  @TestFor(issues = "TW-40689")
  @Test
  public void patch_from_unknown_commit_excluded_root_dir() throws Exception {
    VcsRoot root = getRoot("rename-test");
    String unknownCommit = "hahahahahahahahahahahahahahahahahahahaha";
    checkPatch(root, "patch3", unknownCommit, "1837cf38309496165054af8bf7d62a9fe8997202",
               new CheckoutRules(asList("-:.", //this rule caused NPE
                                        "+:dir with space=>dir with space",
                                        "+:dir1=>dir1",
                                        "+:file_in_branch.txt")));
  }

  @Test(dataProvider = "patchInSeparateProcess")
  public void testPatches(boolean patchInSeparateProcess) throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    checkPatch("cleanPatch1", null, "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");
    checkPatch("patch1", "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", "0dd03338d20d2e8068fbac9f24899d45d443df38");
    checkPatch("patch2", "7e916b0edd394d0fca76456af89f4ff7f7f65049", "049a98762a29677da352405b27b3d910cb94eb3b");
    checkPatch("patch3", null, "1837cf38309496165054af8bf7d62a9fe8997202");
    checkPatch("patch4", "1837cf38309496165054af8bf7d62a9fe8997202", "592c5bcee6d906482177a62a6a44efa0cff9bbc7");
    checkPatch("patch-case", "rename-test", "cbf1073bd3f938e7d7d85718dbc6c3bee10360d9", "2eed4ae6732536f76a65136a606f635e8ada63b9", true);
  }


  @Test(dataProvider = "patchInSeparateProcess")
  public void build_patch_from_later_revision_to_earlier(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    checkPatch("patch5", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46");
  }


  @TestFor(issues = "TW-16530")
  @Test(dataProvider = "patchInSeparateProcess")
  public void build_patch_should_respect_autocrlf(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    VcsRoot root = vcsRoot().withAutoCrlf(true).withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();

    setExpectedSeparator("\r\n");
    checkPatch(root, "patch-eol", null, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", new CheckoutRules("-:dir"));

    String content = new String(getSupport().getContentProvider().getContent("readme.txt", root, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    assertEquals(content, "Test repository for teamcity.change 1\r\nadd feature\r\n");
  }


  @Test(dataProvider = "patchInSeparateProcess")
  public void testSubmodulePatches(boolean patchInSeparateProcess) throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    checkPatch("submodule-added-ignore", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5");
    checkPatch("submodule-removed-ignore", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", "592c5bcee6d906482177a62a6a44efa0cff9bbc7");
    checkPatch("submodule-modified-ignore", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", "37c371a6db0acefc77e3be99d16a44413e746591");
    checkPatch("submodule-added", "patch-tests", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", true);
    checkPatch("submodule-removed", "patch-tests", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", true);
    checkPatch("submodule-modified", "patch-tests", "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", "37c371a6db0acefc77e3be99d16a44413e746591", true);
  }


  @Test(dataProvider = "patchInSeparateProcess")
  public void should_build_patch_on_revision_in_branch_when_cache_is_empty(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    checkPatch("patch.non.default.branch", "master", null, "3df61e6f11a5a9b919cb3f786a83fdd09f058617", false);
  }


  @TestFor(issues = "TW-36551")
  @Test(dataProvider = "patchInSeparateProcess")
  public void should_validate_streamFileThreshold(boolean patchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForPatch(patchInSeparateProcess);
    myConfigBuilder.setStreamFileThreshold(10240); //10Gb, bigger than max int value
    checkPatch("cleanPatch1", null, "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");

    myConfigBuilder.setStreamFileThreshold(-1);
    checkPatch("cleanPatch1", null, "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");
  }


  private void checkPatch(String name, @Nullable String fromVersion, @NotNull String toVersion) throws IOException, VcsException {
    checkPatch(name, "patch-tests", fromVersion, toVersion, false);
  }

  private void checkPatch(String name, @NotNull String branchName, @Nullable String fromVersion, @NotNull String toVersion, boolean enableSubmodules) throws IOException, VcsException {
    setName(name);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot(branchName, enableSubmodules);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root, fromVersion, toVersion, builder, CheckoutRules.DEFAULT);
    builder.close();
    checkPatchResult(output.toByteArray());
  }

  private void checkPatch(@NotNull VcsRoot root, String name, @Nullable String fromVersion, @NotNull String toVersion, @NotNull CheckoutRules rules) throws IOException, VcsException {
    setName(name);
    GitVcsSupport support = getSupport();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root, fromVersion, toVersion, builder, rules);
    builder.close();
    checkPatchResult(output.toByteArray());
  }


  private GitVcsSupport getSupport() {
    return getSupport(null);
  }

  private GitVcsSupport getSupport(@Nullable ExtensionHolder holder) {
    return gitSupport().withPluginConfig(myConfigBuilder)
      .withResetCacheManager(myResetCacheManager)
      .withExtensionHolder(holder)
      .build();
  }

  protected VcsRoot getRoot(@NotNull String branchName) throws IOException {
    return getRoot(branchName, false);
  }

  /**
   * Create a VCS root for the current parameters and specified branch
   *
   * @param branchName       the branch name
   * @param enableSubmodules if true, submodules are enabled
   * @return a created vcs root object
   * @throws IOException if the root could not be created
   */
  protected VcsRoot getRoot(@NotNull String branchName, boolean enableSubmodules) throws IOException {
    return getRoot(branchName, enableSubmodules, myMainRepositoryDir);
  }

  private VcsRootImpl getRoot(@NotNull String branchName, boolean enableSubmodules, File repositoryDir) {
    return vcsRoot().withId(1)
      .withFetchUrl(GitUtils.toURL(repositoryDir))
      .withBranch(branchName)
      .withSubmodulePolicy(enableSubmodules ? SubmodulesCheckoutPolicy.CHECKOUT : SubmodulesCheckoutPolicy.IGNORE)
      .build();
  }


  public static class CheckProxyPropertiesPatchBuilder {
    public static void main(String... args) throws Exception {
      assertSystemProperty("http.proxyHost", "httpProxyHost");
      assertSystemProperty("http.proxyPort", "81");
      assertSystemProperty("https.proxyHost", "httpsProxyHost");
      assertSystemProperty("https.proxyPort", "82");
      assertSystemProperty("http.nonProxyHosts", "some.org");
      assertSystemProperty("teamcity.git.sshProxyType", "http");
      assertSystemProperty("teamcity.git.sshProxyHost", "sshProxyHost");
      assertSystemProperty("teamcity.git.sshProxyPort", "83");
      GitPatchProcess.main(args);
    }

    private static void assertSystemProperty(@NotNull String name, @NotNull String expectedValue) {
      String actualValue = System.getProperty(name);
      if (!expectedValue.equals(actualValue)) {
        if (actualValue == null)
          throw new RuntimeException("System property " + name + " is not specified, expected value " + expectedValue);
        throw new RuntimeException("System property " + name + " = " + actualValue + ", expected value " + expectedValue);
      }
    }
  }
}
