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

import com.jcraft.jsch.JSchException;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.MissingSubmoduleCommitException;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.MissingSubmoduleEntryException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.patches.PatchTestCase;
import jetbrains.vcs.api.ChangeData;
import junit.framework.Assert;
import org.apache.log4j.Level;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.*;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.FileUtil.writeFile;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * The tests for version detection functionality
 */
public class GitVcsSupportTest extends PatchTestCase {

  public static final String VERSION_TEST_HEAD = "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653";
  public static final String CUD1_VERSION = "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7";
  private static final String MERGE_VERSION = "f3f826ce85d6dad25156b2d7550cedeb1a422f4c";
  private static final String MERGE_BRANCH_VERSION = "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009";
  public static final String SUBMODULE_MODIFIED_VERSION = "37c371a6db0acefc77e3be99d16a44413e746591";
  public static final String SUBMODULE_ADDED_VERSION = "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5";
  public static final String SUBMODULE_TXT_ADDED_VERSION = "d1a88fd33c516c1b607db75eb62244b2ea495c42";
  public static final String BEFORE_SUBMODULE_ADDED_VERSION = "592c5bcee6d906482177a62a6a44efa0cff9bbc7";
  public static final String BEFORE_FIRST_LEVEL_SUBMODULE_ADDED_VERSION = "f3f826ce85d6dad25156b2d7550cedeb1a422f4c";
  public static final String AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION = "ce6044093939bb47283439d97a1c80f759669ff5";

  private File myMainRepositoryDir;
  private File myTmpDir;
  private PluginConfigBuilder myConfigBuilder;
  private TempFiles myTempFiles;
  private ResetCacheRegister myResetCacheManager;
  private ServerPaths myServerPaths;
  private File myRepoGitDir;
  private Mockery myContext;

  @BeforeMethod
  public void setUp() throws IOException {
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};
    myContext = new Mockery();
    myTempFiles = new TempFiles();
    myServerPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myConfigBuilder = new PluginConfigBuilder(myServerPaths);
    myRepoGitDir = dataFile("repo.git");
    myTmpDir = myTempFiles.createTempDir();
    myMainRepositoryDir = new File(myTmpDir, "repo.git");
    copyRepository(myRepoGitDir, myMainRepositoryDir);
    copyRepository(dataFile("submodule.git"), new File(myTmpDir, "submodule"));
    copyRepository(dataFile("submodule.git"), new File(myTmpDir, "submodule.git"));
    copyRepository(dataFile("sub-submodule.git"), new File(myTmpDir, "sub-submodule.git"));
    myContext = new Mockery();
    myResetCacheManager = myContext.mock(ResetCacheRegister.class);
    myContext.checking(new Expectations() {{
      allowing(myResetCacheManager).registerHandler(with(any(ResetCacheHandler.class)));
    }});
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  /**
   * Create a VCS root for the current parameters and specified branch
   *
   * @param branchName the branch name
   * @return a created vcs root object
   * @throws IOException if the root could not be created
   */
  protected VcsRoot getRoot(String branchName) throws IOException {
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
  protected VcsRoot getRoot(String branchName, boolean enableSubmodules) throws IOException {
    return getRoot(branchName, enableSubmodules, myMainRepositoryDir);
  }

  private VcsRootImpl getRoot(String branchName, boolean enableSubmodules, File repositoryDir) {
    return vcsRoot().withId(1)
      .withFetchUrl(GitUtils.toURL(repositoryDir))
      .withBranch(branchName)
      .withSubmodulePolicy(enableSubmodules ? SubmodulesCheckoutPolicy.CHECKOUT : SubmodulesCheckoutPolicy.IGNORE)
      .build();
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


  protected String getTestDataPath() {
    return dataFile().getPath();
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCurrentVersion(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("version-test");
    RepositoryStateData state = support.getCurrentState(root);
    String version = state.getBranchRevisions().get(state.getDefaultBranchName());
    assertEquals(VERSION_TEST_HEAD, version);
  }

  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testGetContent(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("version-test");
    RepositoryStateData state = support.getCurrentState(root);
    String version = state.getBranchRevisions().get(state.getDefaultBranchName());
    byte[] data1 = support.getContentProvider().getContent("readme.txt", root, version);
    byte[] data2 = FileUtil.loadFileBytes(dataFile("content", "readme.txt"));
    assertEquals(data1, data2);
    try {
      support.getContentProvider().getContent("non-existing file.txt", root, version);
      fail("The file must not be loaded");
    } catch (VcsFileNotFoundException ex) {
      // ignore exception
    }
  }

  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testGetContentSubmodules(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("patch-tests", true);
    RepositoryStateData state = support.getCurrentState(root);
    String version = state.getBranchRevisions().get(state.getDefaultBranchName());
    byte[] data1 = support.getContentProvider().getContent("submodule/file.txt", root, version);
    byte[] data2 = FileUtil.loadFileBytes(dataFile("content", "submodule file.txt"));
    assertEquals(data1, data2);
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChanges(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    myConfigBuilder.setNumberOfCommitsWhenFromVersionNotFound(3);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");
    // ensure that all revisions reachable from master are fetched
    final List<ModificationData> ms = support.collectChanges(root, VERSION_TEST_HEAD, CUD1_VERSION, new CheckoutRules(""));
    assertEquals(2, ms.size());
    ModificationData m2 = ms.get(1);
    assertEquals("The second commit\n", m2.getDescription());
    assertEquals(3, m2.getChanges().size());
    for (VcsChange ch : m2.getChanges()) {
      assertEquals(VcsChange.Type.ADDED, ch.getType());
      assertEquals("dir/", ch.getFileName().substring(0, 4));
    }
    ModificationData m1 = ms.get(0);
    assertEquals("more changes\n", m1.getDescription());
    assertEquals(CUD1_VERSION, m1.getVersion());
    assertEquals(3, m1.getChanges().size());
    VcsChange ch10 = m1.getChanges().get(0);
    assertEquals("dir/a.txt", ch10.getFileName());
    assertEquals(CUD1_VERSION, ch10.getAfterChangeRevisionNumber());
    assertEquals(m2.getVersion(), ch10.getBeforeChangeRevisionNumber());
    assertEquals(VcsChange.Type.CHANGED, ch10.getType());
    VcsChange ch11 = m1.getChanges().get(1);
    assertEquals("dir/c.txt", ch11.getFileName());
    assertEquals(VcsChange.Type.ADDED, ch11.getType());
    VcsChange ch12 = m1.getChanges().get(2);
    assertEquals("dir/tr.txt", ch12.getFileName());
    assertEquals(VcsChange.Type.REMOVED, ch12.getType());
    // now check merge commit relatively to the branch
    final List<ModificationData> mms0 = support.collectChanges(root, MERGE_BRANCH_VERSION, MERGE_VERSION, new CheckoutRules(""));
    assertEquals(2, mms0.size());
    // no check the merge commit relatively to the fork
    final List<ModificationData> mms1 = support.collectChanges(root, CUD1_VERSION, MERGE_VERSION, new CheckoutRules(""));
    assertEquals(3, mms1.size());
    ModificationData md1 = mms1.get(0);
    assertEquals("merge commit\n", md1.getDescription());
    assertEquals(MERGE_VERSION, md1.getVersion());
    assertEquals(3, md1.getChanges().size());
    VcsChange ch20 = md1.getChanges().get(0);
    assertEquals("dir/a.txt", ch20.getFileName());
    assertEquals(VcsChange.Type.REMOVED, ch20.getType());
    VcsChange ch21 = md1.getChanges().get(1);
    assertEquals("dir/b.txt", ch21.getFileName());
    assertEquals(VcsChange.Type.CHANGED, ch21.getType());
    VcsChange ch22 = md1.getChanges().get(2);
    assertEquals("dir/q.txt", ch22.getFileName());
    assertEquals(VcsChange.Type.ADDED, ch22.getType());
    ModificationData md2 = mms1.get(1);
    assertTrue(md2.isCanBeIgnored());
    assertEquals("b-mod, d-add\n", md2.getDescription());
    assertEquals(MERGE_BRANCH_VERSION, md2.getVersion());
    assertEquals(2, md2.getChanges().size());
    ModificationData md3 = mms1.get(2);
    assertEquals("a-mod, c-rm\n", md3.getDescription());
    assertEquals(2, md3.getChanges().size());
    // check the case with broken commit
    String missing = GitUtils.makeVersion(GitUtils.versionRevision(CUD1_VERSION).replace('0', 'f'), 1238072086000L);
    final List<ModificationData> mms2 = support.collectChanges(root, missing, MERGE_VERSION, new CheckoutRules(""));
    assertEquals(mms2.size(), 3);
  }

  //Test getting changes for the build concurrently. Copy of previous test but with several threads collecting changes
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testConcurrentCollectBuildChanges(boolean fetchInSeparateProcess) throws Throwable {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    myConfigBuilder.setNumberOfCommitsWhenFromVersionNotFound(3);
    final GitVcsSupport support = getSupport();
    final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());

    Runnable r1 = new Runnable() {
      public void run() {
        try {
          // ensure that all revisions reachable from master are fetched
          final VcsRoot root = getRoot("master");
          final List<ModificationData> ms = support.collectChanges(root, VERSION_TEST_HEAD, CUD1_VERSION, new CheckoutRules(""));
          assertEquals(2, ms.size());
          ModificationData m2 = ms.get(1);
          assertEquals("The second commit\n", m2.getDescription());
          assertEquals(3, m2.getChanges().size());
          for (VcsChange ch : m2.getChanges()) {
            assertEquals(VcsChange.Type.ADDED, ch.getType());
            assertEquals("dir/", ch.getFileName().substring(0, 4));
          }
          ModificationData m1 = ms.get(0);
          assertEquals("more changes\n", m1.getDescription());
          assertEquals(CUD1_VERSION, m1.getVersion());
          assertEquals(3, m1.getChanges().size());
          VcsChange ch10 = m1.getChanges().get(0);
          assertEquals("dir/a.txt", ch10.getFileName());
          assertEquals(CUD1_VERSION, ch10.getAfterChangeRevisionNumber());
          assertEquals(m2.getVersion(), ch10.getBeforeChangeRevisionNumber());
          assertEquals(VcsChange.Type.CHANGED, ch10.getType());
          VcsChange ch11 = m1.getChanges().get(1);
          assertEquals("dir/c.txt", ch11.getFileName());
          assertEquals(VcsChange.Type.ADDED, ch11.getType());
          VcsChange ch12 = m1.getChanges().get(2);
          assertEquals("dir/tr.txt", ch12.getFileName());
          assertEquals(VcsChange.Type.REMOVED, ch12.getType());
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r2 = new Runnable() {
      public void run() {
        try {
          // now check merge commit relatively to the branch
          final VcsRoot root = getRoot("master");
          final List<ModificationData> mms0 = support.collectChanges(root, MERGE_BRANCH_VERSION, MERGE_VERSION, new CheckoutRules(""));
          assertEquals(2, mms0.size());
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r3 = new Runnable() {
      public void run() {
        try {
          // no check the merge commit relatively to the fork
          final VcsRoot root = getRoot("master");
          final List<ModificationData> mms1 = support.collectChanges(root, CUD1_VERSION, MERGE_VERSION, new CheckoutRules(""));
          assertEquals(3, mms1.size());
          ModificationData md1 = mms1.get(0);
          assertEquals("merge commit\n", md1.getDescription());
          assertEquals(MERGE_VERSION, md1.getVersion());
          assertEquals(3, md1.getChanges().size());
          VcsChange ch20 = md1.getChanges().get(0);
          assertEquals("dir/a.txt", ch20.getFileName());
          assertEquals(VcsChange.Type.REMOVED, ch20.getType());
          VcsChange ch21 = md1.getChanges().get(1);
          assertEquals("dir/b.txt", ch21.getFileName());
          assertEquals(VcsChange.Type.CHANGED, ch21.getType());
          VcsChange ch22 = md1.getChanges().get(2);
          assertEquals("dir/q.txt", ch22.getFileName());
          assertEquals(VcsChange.Type.ADDED, ch22.getType());
          ModificationData md2 = mms1.get(1);
          assertTrue(md2.isCanBeIgnored());
          assertEquals("b-mod, d-add\n", md2.getDescription());
          assertEquals(MERGE_BRANCH_VERSION, md2.getVersion());
          assertEquals(2, md2.getChanges().size());
          ModificationData md3 = mms1.get(2);
          assertEquals("a-mod, c-rm\n", md3.getDescription());
          assertEquals(2, md3.getChanges().size());
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    Runnable r4 = new Runnable() {
      public void run() {
        try {
          // check the case with broken commit
          final VcsRoot root = getRoot("master");
          String missing = GitUtils.makeVersion(GitUtils.versionRevision(CUD1_VERSION).replace('0', 'f'), 1238072086000L);
          final List<ModificationData> mms2 = support.collectChanges(root, missing, MERGE_VERSION, new CheckoutRules(""));
          assertEquals(mms2.size(), 3);
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    BaseTestCase.runAsyncAndFailOnException(4, r1, r2, r3, r4);

    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubmodulesIgnored(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("patch-tests");
    final List<ModificationData> ms =
      support.collectChanges(root, BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION, new CheckoutRules(""));
    assertEquals(1, ms.size());
    ModificationData m1 = ms.get(0);
    assertEquals("added submodule\n", m1.getDescription());
    assertEquals(2, m1.getChanges().size());
    VcsChange ch11 = m1.getChanges().get(0);
    assertEquals(VcsChange.Type.ADDED, ch11.getType());
    assertEquals(".gitmodules", ch11.getFileName());
    VcsChange ch12 = m1.getChanges().get(1);
    assertEquals("submodule", ch12.getFileName());
    assertEquals(VcsChange.Type.ADDED, ch12.getType());
    final List<ModificationData> ms2 =
      support.collectChanges(root, SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION, new CheckoutRules(""));
    assertEquals(1, ms.size());
    ModificationData m2 = ms2.get(0);
    assertEquals("submodule updated\n", m2.getDescription());
    assertEquals(1, m2.getChanges().size());
    VcsChange ch21 = m2.getChanges().get(0);
    assertEquals("submodule", ch21.getFileName());
    assertEquals(VcsChange.Type.CHANGED, ch21.getType());
  }

  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubmodules(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("patch-tests", true);
    final List<ModificationData> ms =
      support.collectChanges(root, BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION, new CheckoutRules(""));
    assertEquals(1, ms.size());
    ModificationData m1 = ms.get(0);
    assertEquals("added submodule\n", m1.getDescription());
    assertEquals(2, m1.getChanges().size());
    VcsChange ch11 = m1.getChanges().get(0);
    assertEquals(VcsChange.Type.ADDED, ch11.getType());
    assertEquals(".gitmodules", ch11.getFileName());
    VcsChange ch12 = m1.getChanges().get(1);
    assertEquals("submodule/file.txt", ch12.getFileName());
    assertEquals(VcsChange.Type.ADDED, ch12.getType());
    final List<ModificationData> ms2 =
      support.collectChanges(root, SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION, new CheckoutRules(""));
    assertEquals(1, ms.size());
    ModificationData m2 = ms2.get(0);
    assertEquals("submodule updated\n", m2.getDescription());
    assertEquals(1, m2.getChanges().size());
    VcsChange ch21 = m2.getChanges().get(0);
    assertEquals("submodule/new file.txt", ch21.getFileName());
    assertEquals(VcsChange.Type.ADDED, ch21.getType());
  }


  /*
   * o fix submodule entry again but track newer revision | e6b15b1f4741199857e2fa744eaadfe5a9d9aede
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o broke submodule entry again                        | feac610f381e697acf4c1c8ad82b7d76c7643b04
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o fix submodule entry again                          | 92112555d9eb3e433eaa91fe32ec001ae8fe3c52
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o broke submodule entry again                        | 778cc3d0105ca1b6b2587804ebfe89c2557a7e46
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o finally add correct entry                          | f5bdd3819df0358a43d9a8f94eaf96bb306e19fe
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o add entry for unrelated submodule                  | 78cbbed3561de3417467ee819b1795ba14c03dfb
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o add empty .gitmodules                              | 233daeefb335b60c7b5700afde97f745d86cb40d
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o add submodule wihtout entry in .gitmodules         | 6eae9acd29db2dba146929634a4bb1e6e72a31fd
   * |                                                    |
   * |                                                    |
   * v                                                    |
   * o no submodules                                      | f3f826ce85d6dad25156b2d7550cedeb1a422f4c (merge_version)
   *
   */
  @TestFor(issues = "TW-13127")
  @Test
  public void testCollectBuildChangesWithBrokenSubmoduleOnLastCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    try {
      support.collectChanges(root, MERGE_VERSION, "78cbbed3561de3417467ee819b1795ba14c03dfb", CheckoutRules.DEFAULT);
      fail("We should throw exception if submodules in the last commit are broken");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof MissingSubmoduleEntryException);
      MissingSubmoduleEntryException e1 = (MissingSubmoduleEntryException) e.getCause();
      assertEquals("The repository '" + root.getProperty(Constants.FETCH_URL) + "' " +
                   "has a submodule in the commit '78cbbed3561de3417467ee819b1795ba14c03dfb' " +
                   "at a path 'submodule-wihtout-entry', but has no entry for this path in .gitmodules configuration",
                   e1.getMessage());
    }
  }


  @Test
  public void should_throw_descriptive_error_when_referenced_commit_not_found() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("reference-wrong-commit", true);
    try {
      support.collectChanges(root, MERGE_VERSION, "7253d358a2490321a1808a1c20561b4027d69f77", CheckoutRules.DEFAULT);
      fail();
    } catch (VcsException e) {
      assertTrue(e.getCause() instanceof MissingSubmoduleCommitException);
      MissingSubmoduleCommitException e1 = (MissingSubmoduleCommitException) e.getCause();
      assertEquals("Cannot find the commit ded023a236d184753f826e62ac16b1612060e9d0 " +
                   "in the repository '../submodule' used as a submodule " +
                   "by the repository '" + root.getProperty(Constants.FETCH_URL) + "' " +
                   "in the commit 7253d358a2490321a1808a1c20561b4027d69f77 " +
                   "at a path 'submodule-with-dirs'",
                   e1.getMessage());
    }
  }


  @Test
  public void testCollectBuildChangesWithFixedSubmoduleOnLastCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    List<ModificationData> mds = support.collectChanges(root, MERGE_VERSION, "f5bdd3819df0358a43d9a8f94eaf96bb306e19fe", CheckoutRules.DEFAULT);
    assertEquals(mds.size(), 4);
    assertEquals(mds.get(0).getChanges().size(), 2);
  }


  @Test
  public void testCollectBuildChangesWithFixedBrokenFixedSubmodule() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fixedSubmoduleCommit = "f5bdd3819df0358a43d9a8f94eaf96bb306e19fe";
    String submoduleFixedAgainCommit = "92112555d9eb3e433eaa91fe32ec001ae8fe3c52";
    List<ModificationData> mds = support.collectChanges(root, fixedSubmoduleCommit, submoduleFixedAgainCommit, CheckoutRules.DEFAULT);
    assertEquals(2, mds.size());
    for (ModificationData md : mds) {
      assertEquals(md.getChanges().size(), 1); //this means we don't report remove and add of all submodule files
    }
  }


  @TestFor(issues = "TW-19544")
  @Test
  public void testCollectChangesWithBrokenSubmoduleOnLastCommitAndUsualFileInsteadOfSubmoduleInPreviousCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fromCommit = "f5bdd3819df0358a43d9a8f94eaf96bb306e19fe";
    String lastCommit = "39679cc440c83671fbf6ad8083d92517f9602300";
    support.collectChanges(root, fromCommit, lastCommit, CheckoutRules.DEFAULT);
  }


  @Test
  public void should_not_traverse_history_deeper_than_specified_limit() throws Exception {
    myConfigBuilder.setFixedSubmoduleCommitSearchDepth(0);//do no search submodule commit with fix at all
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fixedSubmoduleCommit = "f5bdd3819df0358a43d9a8f94eaf96bb306e19fe";
    String submoduleFixedAgainCommit = "92112555d9eb3e433eaa91fe32ec001ae8fe3c52";
    List<ModificationData> mds = support.collectChanges(root, fixedSubmoduleCommit, submoduleFixedAgainCommit, CheckoutRules.DEFAULT);
    assertEquals(2, mds.size());
    assertEquals(2, mds.get(0).getChanges().size());//that means we did not try to find commit with fixed submodule
    //we report add of all files from submodule repository, so the first change is the change to .gitmodules,
    //and the second - is the addition of file.txt from submodule
  }


  @Test
  public void testCollectBuildChangesWithFixedBrokenFixedSubmodule2() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fixedSubmoduleCommit = GitUtils.makeVersion("92112555d9eb3e433eaa91fe32ec001ae8fe3c52", 1282636308000L);
    String submoduleFixedAgainCommit = GitUtils.makeVersion("e6b15b1f4741199857e2fa744eaadfe5a9d9aede", 1282813085000L);
    List<ModificationData> mds = support.collectChanges(root, fixedSubmoduleCommit, submoduleFixedAgainCommit, new CheckoutRules(""));
    assertEquals(2, mds.size());

    assertEquals(mds.get(1).getChanges().size(), 1);//only .gitmodules

    assertEquals(mds.get(0).getChanges().size(), 2);//.gitmodules and 1 file inside submodule
  }

  @Test
  public void testSubmoduleWithDirs() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String beforeSubmodWithDirCommit = GitUtils.makeVersion("e6b15b1f4741199857e2fa744eaadfe5a9d9aede", 1282822922000L);
    String submodWithDirCommit = GitUtils.makeVersion("6cf3cb6a87091d17466607858c699c35edf30d3b", 1289297786000L);
    support.collectChanges(root, beforeSubmodWithDirCommit, submodWithDirCommit, CheckoutRules.DEFAULT);
  }


  // Test collecting changes with non-recursive submodule checkout: only first level submodule files are checked out
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubSubmodulesNonRecursive(boolean fetchInSeparateProcess) throws Exception {
    checkCollectBuildChangesSubSubmodules(fetchInSeparateProcess, false);
  }


  // Test collecting changes with recursive submodule checkout: submodules of submodules are checked out
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubSubmodulesRecursive(boolean fetchInSeparateProcess) throws Exception {
    checkCollectBuildChangesSubSubmodules(fetchInSeparateProcess, true);
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void should_support_submodule_on_tag(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("submodule-on-tag", true);
    support.collectChanges(root,
                           GitUtils.makeVersion("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", 1289483394000L),
                           GitUtils.makeVersion("f61e30ce576e76bff877ddf1d00acf22c5c1b07a", 1320317743000L),
                           CheckoutRules.DEFAULT);
  }


  private void checkCollectBuildChangesSubSubmodules(boolean fetchInSeparateProcess, boolean recursiveSubmoduleCheckout)
    throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);

    Set<String> subSubmoduleFileNames = new HashSet<String>();
    subSubmoduleFileNames.add("first-level-submodule/sub-sub/file.txt");
    subSubmoduleFileNames.add("first-level-submodule/sub-sub/new file.txt");

    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("sub-submodule", true);
    if (!recursiveSubmoduleCheckout) {
      ((VcsRootImpl) root).addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT.name());
    }
    final List<ModificationData> ms = support.collectChanges(root,
                                                             BEFORE_FIRST_LEVEL_SUBMODULE_ADDED_VERSION,
                                                             AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION,
                                                             new CheckoutRules(""));
    boolean subSubmoduleFilesRetrieved = false;
    boolean firstLevelSubmoduleFilesRetrieved = false;
    assertEquals(1, ms.size());
    ModificationData m1 = ms.get(0);
    for (VcsChange change : m1.getChanges()) {
      if (subSubmoduleFileNames.contains(change.getFileName())) {
        subSubmoduleFilesRetrieved = true;
      }
      if ("first-level-submodule/submoduleFile.txt".equals(change.getFileName())) {
        firstLevelSubmoduleFilesRetrieved = true;
      }
    }
    assertTrue(firstLevelSubmoduleFilesRetrieved);
    if (recursiveSubmoduleCheckout) {
      assertTrue(subSubmoduleFilesRetrieved);
    } else {
      assertFalse(subSubmoduleFilesRetrieved);
    }
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void check_buildPatch_understands_revisions_with_timestamps(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    checkPatch("cleanPatch1", null, GitUtils.makeVersion("a894d7d58ffde625019a9ecf8267f5f1d1e5c341", 1237391915000L));
    checkPatch("patch1",
               GitUtils.makeVersion("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", 1238420977000L),
               GitUtils.makeVersion("0dd03338d20d2e8068fbac9f24899d45d443df38", 1238421020000L));
  }

  @Test
  public void collect_changes_should_understand_revisions_with_timestamps() throws Exception {
    VcsRoot root = getRoot("master");
    List<ModificationData> changes = getSupport().collectChanges(root,
                                GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915000L),
                                GitUtils.makeVersion("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", 1238085489000L),
                                CheckoutRules.DEFAULT);
    assertEquals(changes.size(), 3);
  }

  @Test
  @TestFor(issues = "TW-30485")
  public void collect_changes_between_states_should_understand_revisions_with_timestamps() throws Exception {
    VcsRoot root = vcsRoot().withBranch("refs/heads/master").withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();
    RepositoryStateData fromStateInOldFormat = RepositoryStateData.createSingleVersionState(GitUtils.makeVersion("2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", 1286183770000L));
    RepositoryStateData toState = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    getSupport().getCollectChangesPolicy().collectChanges(root, fromStateInOldFormat, toState, CheckoutRules.DEFAULT);
  }

  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testPatches(boolean fetchInSeparateProcess) throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    checkPatch("cleanPatch1", null, "a894d7d58ffde625019a9ecf8267f5f1d1e5c341");
    checkPatch("patch1", "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", "0dd03338d20d2e8068fbac9f24899d45d443df38");
    checkPatch("patch2", "7e916b0edd394d0fca76456af89f4ff7f7f65049", "049a98762a29677da352405b27b3d910cb94eb3b");
    checkPatch("patch3", null, "1837cf38309496165054af8bf7d62a9fe8997202");
    checkPatch("patch4", "1837cf38309496165054af8bf7d62a9fe8997202", "592c5bcee6d906482177a62a6a44efa0cff9bbc7");
    checkPatch("patch-case", "rename-test", "cbf1073bd3f938e7d7d85718dbc6c3bee10360d9", "2eed4ae6732536f76a65136a606f635e8ada63b9", true);
  }


  @Test
  public void build_patch_from_later_revision_to_earlier() throws Exception {
    checkPatch("patch5", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46");
  }


  //Due to changes in the logic of choosing checkout directory on the agent (builds
  //from the same repository go to the same dir even if the branches are different), git-plugin
  //should be able to decide if it can build an incremental patch, or full patch is required.
  //There are 2 possible cases: revision from which we build the patch is found in the local clone
  //of repository on the server or not. If revision is not found - git-plugin should build a full patch.
  //2 following tests test this behaviour

  @Test
  public void build_patch_between_unrelated_revisions_when_from_version_is_fetched() throws Exception {
    checkPatch("patch6", "rename-test", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "2eed4ae6732536f76a65136a606f635e8ada63b9", false);
  }


  @Test
  public void build_patch_between_unrelated_revisions_when_from_version_is_not_fetched() throws Exception {
    //fromRevision (592c5bcee6d906482177a62a6a44efa0cff9bbc7) is not found in a repository clone on the server:
    VcsRoot root = getRoot("rename-test");
    checkPatch(root, "patch7", "592c5bcee6d906482177a62a6a44efa0cff9bbc7", "2eed4ae6732536f76a65136a606f635e8ada63b9", new CheckoutRules("+:.=>path"));
  }


  @TestFor(issues = "TW-16530")
  @Test
  public void build_patch_should_respect_autocrlf() throws Exception {
    VcsRoot root = vcsRoot().withAutoCrlf(true).withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();

    setExpectedSeparator("\r\n");
    checkPatch(root, "patch-eol", null, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", new CheckoutRules("-:dir"));

    String content = new String(getSupport().getContentProvider().getContent("readme.txt", root, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    assertEquals(content, "Test repository for teamcity.change 1\r\nadd feature\r\n");
  }


  @Test
  public void default_autocrlf_should_not_be_included_in_checkout_properties() throws VcsException {
    VcsRoot root = vcsRoot().withAutoCrlf(false).withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();
    Map<String, String> checkoutProperties = getSupport().getCheckoutProperties(root);
    assertFalse(checkoutProperties.containsKey(Constants.SERVER_SIDE_AUTO_CRLF),
                "Introduction of autocrlf cause clean checkout");
  }


  @Test
  public void non_default_autocrlf_should_be_reported_in_checkout_properties() throws VcsException {
    VcsRoot root = vcsRoot().withAutoCrlf(true).withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();
    Map<String, String> checkoutProperties = getSupport().getCheckoutProperties(root);
    assertTrue(checkoutProperties.containsKey(Constants.SERVER_SIDE_AUTO_CRLF),
               "Autocrlf is not reported in checkout properties");
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testSubmodulePatches(boolean fetchInSeparateProcess) throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    checkPatch("submodule-added-ignore", BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION);
    checkPatch("submodule-removed-ignore", SUBMODULE_ADDED_VERSION, BEFORE_SUBMODULE_ADDED_VERSION);
    checkPatch("submodule-modified-ignore", SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION);
    checkPatch("submodule-added", "patch-tests", BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION, true);
    checkPatch("submodule-removed", "patch-tests", SUBMODULE_ADDED_VERSION, BEFORE_SUBMODULE_ADDED_VERSION, true);
    checkPatch("submodule-modified", "patch-tests", SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION, true);
  }


  private void checkPatch(final String name, @Nullable final String fromVersion, final String toVersion) throws IOException, VcsException {
    checkPatch(name, "patch-tests", fromVersion, toVersion, false);
  }

  /**
   * Check single patch
   *
   * @param name             the name of patch
   * @param branchName       the name of branch to use
   * @param fromVersion      from version
   * @param toVersion        to version
   * @param enableSubmodules if true, submodules are enabled
   * @throws IOException  in case of test failure
   * @throws VcsException in case of test failure
   */
  private void checkPatch(String name, String branchName, String fromVersion, String toVersion, boolean enableSubmodules) throws IOException, VcsException {
    setName(name);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot(branchName, enableSubmodules);
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root, fromVersion, toVersion, builder, new CheckoutRules(""));
    builder.close();
    checkPatchResult(output.toByteArray());
  }

  private void checkPatch(@NotNull VcsRoot root, String name, @Nullable String fromVersion, String toVersion, CheckoutRules rules) throws IOException, VcsException {
    setName(name);
    GitVcsSupport support = getSupport();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root, fromVersion, toVersion, builder, rules);
    builder.close();
    checkPatchResult(output.toByteArray());
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testLabels(boolean fetchInSeparateProcess) throws IOException, VcsException, URISyntaxException {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");
    // ensure that all revisions reachable from master are fetched
    support.getLabelingSupport().label("test_label", VERSION_TEST_HEAD, root, new CheckoutRules(""));
    Repository r = new RepositoryBuilder().setGitDir(new File(new URIish(root.getProperty(Constants.FETCH_URL)).getPath())).build();
    RevWalk revWalk = new RevWalk(r);
    try {
      Ref tagRef = r.getTags().get("test_label");
      RevTag t = revWalk.parseTag(tagRef.getObjectId());
      assertEquals(t.getObject().name(), GitUtils.versionRevision(VERSION_TEST_HEAD));
    } finally {
      r.close();
    }
  }


  @Test
  public void tag_with_specified_username() throws Exception {
    VcsRoot root = vcsRoot()
      .withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withUsernameForTags("John Doe <john.doe@some.org>")
      .build();
    GitVcsSupport git = getSupport();
    git.getLabelingSupport().label("label_with_specified_username", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", root, CheckoutRules.DEFAULT);

    Repository r = new RepositoryBuilder().setGitDir(new File(new URIish(root.getProperty(Constants.FETCH_URL)).getPath())).build();
    RevWalk revWalk = new RevWalk(r);
    try {
      Ref tagRef = r.getTags().get("label_with_specified_username");
      RevTag t = revWalk.parseTag(tagRef.getObjectId());
      PersonIdent tagger = t.getTaggerIdent();
      assertEquals(tagger.getName(), "John Doe");
      assertEquals(tagger.getEmailAddress(), "john.doe@some.org");
    } finally {
      revWalk.release();
      r.close();
    }
  }


  @Test
  @TestFor(issues = "TW-23423")
  public void relative_path_to_repository_should_go_under_git_caches_dir() throws VcsException {
    String relativePath = "some" + File.separator + "relative" + File.separator + "path";
    VcsRoot root = vcsRoot().withId(1)
      .withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withRepositoryPathOnServer(relativePath)
      .build();
    GitVcsSupport git = getSupport();
    git.collectChanges(root, "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", "78cbbed3561de3417467ee819b1795ba14c03dfb", CheckoutRules.DEFAULT);
    assertTrue(new File(myServerPaths.getCachesDir(), "git" + File.separator + relativePath).exists());
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testMapFullPath(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    final GitVcsSupport support = getSupport();
    final VcsRoot root = getRoot("master");

    final String repositoryUrl = root.getProperty(Constants.FETCH_URL);
    final List<Exception> errors = new ArrayList<Exception>();

    Runnable collectChanges = new Runnable() {
      public void run() {
        try {
          support.collectChanges(root, VERSION_TEST_HEAD, CUD1_VERSION, new CheckoutRules(""));
          Thread.sleep(100);
          support.collectChanges(root, VERSION_TEST_HEAD, MERGE_BRANCH_VERSION, new CheckoutRules(""));
          Thread.sleep(100);
          support.collectChanges(root, VERSION_TEST_HEAD, MERGE_VERSION, new CheckoutRules(""));
          Thread.sleep(100);
        } catch (Exception e) {
          errors.add(e);
        }
      }
    };

    Runnable mapFullPath = new Runnable() {
      public void run() {
        try {
          final VcsRootEntry rootEntry = new VcsRootEntry(root, CheckoutRules.DEFAULT);
          for (int i = 0; i < 5; i++) {
            support.mapFullPath(rootEntry, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ad4528ed5c84092fdbe9e0502163cf8d6e6141e8|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "f3f826ce85d6dad25156b2d7550cedeb1a422f4c|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ad4528ed5c84092fdbe9e0502163cf8d6e6141e9|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ad4528ed5c84092fdbe9e0502163cf8d6e6141f0|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(rootEntry, "ad4528ed5c84092fdbe9e0502163cf8d6e6141f1|" + repositoryUrl + "|readme.txt");
          }
        } catch (Exception e) {
          errors.add(e);
        }
      }
    };

    support.collectChanges(root, VERSION_TEST_HEAD, VERSION_TEST_HEAD, new CheckoutRules(""));
    BaseTestCase.runAsyncAndFailOnException(4, collectChanges, mapFullPath);

    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }


  /**
   * Test path normalization
   */
  @Test
  public void testNormalizedPaths() {
    String[][] data = {
      {"/../aa/../b", "/../b"},
      {"..", ".."},
      {".", ""},
      {"../aa/../b", "../b"},
      {"/////../aa///..//.//q", "/../q"},
    };
    for (String[] d : data) {
      assertEquals(GitUtils.normalizePath(d[0]), d[1]);
    }
  }


  /*
   * Test reproduces a bug in Fetcher code: Fetcher worked only if all parameters of VcsRoot
   * sent to process input as string were smaller than 512 bytes (most of the cases) or size mod 512 = 0.
   */
  @TestFor(issues = "TW-13330")
  @Test
  public void test_long_input_for_fetcher_process() throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForFetch(true);
    GitVcsSupport support = getSupport();
    VcsRootImpl root = (VcsRootImpl) getRoot("version-test");
    root.addProperty("param",
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" +
                     "bbbbbbbbbbbbbbbbbbbbb");//with such long param size of input for fetcher process is greater than 512 bytes
    support.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", CheckoutRules.DEFAULT);
  }


  @Test
  public void test_display_name_conflict() {
    Mockery context = new Mockery();
    final ExtensionHolder holder = context.mock(ExtensionHolder.class);
    final VcsSupportContext anotherGitPlugin = context.mock(VcsSupportContext.class);
    final VcsSupportCore anotherGitPluginCore = context.mock(VcsSupportCore.class);
    context.checking(new Expectations() {{
      allowing(holder).getServices(VcsSupportContext.class); will(returnValue(Arrays.asList(anotherGitPlugin)));
      allowing(anotherGitPlugin).getCore(); will(returnValue(anotherGitPluginCore));
      allowing(anotherGitPluginCore).getName(); will(returnValue("git"));
    }});
    GitVcsSupport jetbrainsPlugin = getSupport(holder);
    assertEquals(jetbrainsPlugin.getDisplayName(), "Git (JetBrains plugin)");
  }


  @Test
  public void test_display_name_no_conflict() {
    Mockery context = new Mockery();
    final ExtensionHolder holder = context.mock(ExtensionHolder.class);
    final VcsSupportContext anotherVcsPlugin = context.mock(VcsSupportContext.class);
    final VcsSupportCore anotherVcsPluginCore = context.mock(VcsSupportCore.class);
    context.checking(new Expectations() {{
      allowing(holder).getServices(VcsSupportContext.class);
      will(returnValue(Arrays.asList(anotherVcsPlugin)));
      allowing(anotherVcsPlugin).getCore();
      will(returnValue(anotherVcsPluginCore));
      allowing(anotherVcsPluginCore).getName();
      will(returnValue("hg"));
    }});
    GitVcsSupport jetbrainsPlugin = getSupport(holder);
    assertEquals(jetbrainsPlugin.getDisplayName(), "Git");
  }


  @TestFor(issues = "TW-14813")
  @Test
  public void test_logging() {
    myConfigBuilder.setSeparateProcessForFetch(true);

    String noDebugError = getFetchExceptionMessage();
    assertFalse(noDebugError.contains("at jetbrains.buildServer.buildTriggers.vcs.git.Fetcher"));//no stacktrace
    assertFalse(noDebugError.endsWith("\n"));

    Loggers.VCS.setLevel(Level.DEBUG);
    String debugError = getFetchExceptionMessage();
    assertTrue(debugError.contains("at jetbrains.buildServer.buildTriggers.vcs.git.Fetcher"));
    assertFalse(debugError.endsWith("\n"));
  }


  /*
   * Repository cloned by hand could have no teamcity.remote config, we should create it otherwise we can see 'null' as remote url in error messages
   * (see log in the issue for details).
   */
  @TestFor(issues = "TW-15564")
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void should_create_teamcity_config_in_root_with_custom_path(boolean fetchInSeparateProcess) throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    File customRootDir = new File(myTmpDir, "custom-dir");
    VcsRootImpl root = (VcsRootImpl) getRoot("master");
    root.addProperty(Constants.PATH, customRootDir.getAbsolutePath());
    getSupport().getCurrentState(root);

    File configFile = new File(customRootDir, "config");
    String config = FileUtil.readText(configFile);
    Pattern pattern = Pattern.compile("(.*)\\[teamcity\\]\\s+remote = " + Pattern.quote(GitUtils.toURL(myMainRepositoryDir)) + "\\s*(.*)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(config);
    assertTrue(matcher.matches(), "config is " + config);

    //erase teamcity.remote config
    String newConfig = matcher.group(1) + matcher.group(2);
    writeFile(configFile, newConfig);

    getSupport().getCurrentState(root);
    config = FileUtil.readText(configFile);
    assertTrue(pattern.matcher(config).matches());
  }


  @Test
  public void should_support_git_refs_format() throws IOException, VcsException {
    RepositoryStateData state1 = getSupport().getCurrentState(getRoot("version-test"));
    RepositoryStateData state2 = getSupport().getCurrentState(getRoot("refs/heads/version-test"));
    assertEquals(state1.getBranchRevisions().get(state1.getDefaultBranchName()),
                 state2.getBranchRevisions().get(state2.getDefaultBranchName()));
    RepositoryStateData state3 = getSupport().getCurrentState(getRoot("master"));
    RepositoryStateData state4 = getSupport().getCurrentState(getRoot("refs/heads/master"));
    assertEquals(state3.getBranchRevisions().get(state3.getDefaultBranchName()),
                 state4.getBranchRevisions().get(state4.getDefaultBranchName()));
  }


  @Test
  @TestFor(issues = "TW-17910")
  public void fetch_process_should_respect_fetch_timeout() throws Exception {
    //MockFetcher waits for 10 seconds
    //set teamcity.execution.timeout = 2, we should not get TimeoutException
    Properties beforeTestProperties = System.getProperties();
    final String defaultProcessExecutionTimeoutProperty = "teamcity.execution.timeout";
    System.setProperty(defaultProcessExecutionTimeoutProperty, "2");
    try {
      String classpath = myConfigBuilder.build().getFetchClasspath() + File.pathSeparator +
                         ClasspathUtil.composeClasspath(new Class[]{MockFetcher.class}, null, null);
      myConfigBuilder.setSeparateProcessForFetch(true)
        .setFetchClasspath(classpath)
        .setFetcherClassName(MockFetcher.class.getName());

      final GitVcsSupport support = getSupport();
      final VcsRootImpl root = (VcsRootImpl) getRoot("master");
      support.collectChanges(root, VERSION_TEST_HEAD, MERGE_BRANCH_VERSION, CheckoutRules.DEFAULT);
    } catch (Exception e) {
      fail(e.getMessage());
    } finally {
      System.setProperties(beforeTestProperties);
    }
  }


  @Test
  public void fetch_process_should_have_necessary_options_from_internal_properties() throws Exception {
    String classpath = myConfigBuilder.build().getFetchClasspath() + File.pathSeparator +
                       ClasspathUtil.composeClasspath(new Class[]{FetcherCheckingProperties.class,
                         PatchTestCase.class,
                         org.testng.Assert.class}, null, null);
    myConfigBuilder.setSeparateProcessForFetch(true)
      .setFetchClasspath(classpath)
      .setFetcherClassName(FetcherCheckingProperties.class.getName()) //custom Fetcher that throws a error if it cannot find specific property
      .withFetcherProperties("teamcity.git.some.prop", "123");

    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");

    //custom Fetcher throws a error if TeamCityProperty teamcity.git.some.prop != 123, collect changes would fail in this case
    support.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", CheckoutRules.DEFAULT);
  }

  public static class FetcherCheckingProperties {
    public static void main(String... args) throws Exception {
      Fetcher.main(args);
      if (!"123".equals(TeamCityProperties.getProperty("teamcity.git.some.prop")))
        throw new Exception("Property teamcity.git.some.prop is not passed to the Fetcher");
    }
  }


  //@Test
  public void collecting_changes_should_not_block_IDE_requests() throws Exception {
    String classpath = myConfigBuilder.build().getFetchClasspath() + File.pathSeparator +
                       ClasspathUtil.composeClasspath(new Class[]{MockFetcher.class}, null, null);
    myConfigBuilder.setSeparateProcessForFetch(true)
      .setFetchClasspath(classpath)
      .setFetcherClassName(MockFetcher.class.getName());

    final GitVcsSupport support = getSupport();

    final VcsRootImpl root = (VcsRootImpl) getRoot("master");
    final File customRootDir = new File(myTmpDir, "custom-dir");
    root.addProperty(Constants.PATH, customRootDir.getAbsolutePath());
    final String repositoryUrl = root.getProperty(Constants.FETCH_URL);

    final AtomicInteger succeedIDERequestCount = new AtomicInteger(0);
    final AtomicBoolean fetchBlocksIDERequests = new AtomicBoolean(false);
    final List<Exception> errors = Collections.synchronizedList(new ArrayList<Exception>());

    Runnable longFetch = new Runnable () {
      public void run() {
        try {
          support.collectChanges(root, VERSION_TEST_HEAD, MERGE_VERSION, new CheckoutRules(""));
          fetchBlocksIDERequests.set(succeedIDERequestCount.get() == 0);
        } catch (VcsException e) {
          errors.add(e);
        }
      }
    };

    Runnable requestsFromIDE = new Runnable() {
      public void run() {
        while (!new File(customRootDir, "mock.lock").exists()) {//wait for fetch to begin (MockFetcher holds a lock during fetch)
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        LockFile lock = new LockFile(new File(customRootDir.getAbsolutePath(), "mock"), FS.DETECTED);
        try {
          while (true) {//do mapFullPath while fetch is executed (we cannot acquire a lock while it is executed)
            if (!lock.lock()) {
              support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")), GitUtils.versionRevision(VERSION_TEST_HEAD) + "|" + repositoryUrl + "|readme.txt");
              succeedIDERequestCount.incrementAndGet();
            } else {
              lock.unlock();
              break;
            }
          }
        } catch (Exception e) {
          errors.add(e);
        }
      }
    };

    Thread fetch = new Thread(longFetch);
    Thread ideRequests = new Thread(requestsFromIDE);
    fetch.start();
    ideRequests.start();
    fetch.join();
    ideRequests.join();
    if (!errors.isEmpty()) {
      fail("Errors in readers thread", errors.get(0));
    }

    assertFalse(fetchBlocksIDERequests.get());
  }


  @TestFor(issues = "TW-16351")
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void should_update_local_ref_when_it_locked(boolean fetchInSeparateProcess) throws Exception {
    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    copyRepository(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRootImpl root = getRoot("master", false, remoteRepositoryDir);
    String branch = root.getProperty(Constants.BRANCH_NAME);
    File customRootDir = new File(myTmpDir, "custom-dir");
    root.addProperty(Constants.PATH, customRootDir.getAbsolutePath());

    RepositoryStateData state = support.getCurrentState(root);
    String v1 = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
    support.collectChanges(root, "a7274ca8e024d98c7d59874f19f21d26ee31d41d", "add81050184d3c818560bdd8839f50024c188586", CheckoutRules.DEFAULT);

    copyRepository(dataFile("repo_for_fetch.2"), remoteRepositoryDir);//now remote repository contains new commits

    File branchLockFile = createBranchLockFile(customRootDir, branch);
    assertTrue(branchLockFile.exists());

    state = support.getCurrentState(root);
    String v2 = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
    assertFalse(v2.equals(v1));//local repository is updated

    support.collectChanges(root, v1, v2, CheckoutRules.DEFAULT);
  }


  @TestFor(issues = "TW-16351")
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void test_non_fast_forward_update(boolean fetchInSeparateProcess) throws Exception {
    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    copyRepository(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master", false, remoteRepositoryDir);

    RepositoryStateData state = support.getCurrentState(root);
    String v1 = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
    assertEquals(v1, "add81050184d3c818560bdd8839f50024c188586");

    copyRepository(dataFile("repo_for_fetch.2"), remoteRepositoryDir);//fast-forward update

    state = support.getCurrentState(root);
    String v2 = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
    assertEquals(v2, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168");

    copyRepository(dataFile("repo_for_fetch.3"), remoteRepositoryDir);//non-fast-forward update
    state = support.getCurrentState(root);
    String v3 = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
    assertEquals("bba7fbcc200b4968e6abd2f7d475dc15306cafc6", v3);
  }


  @Test
  public void all_changes_should_have_parents() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");

    //Every git commit (except initial commit) has at least one parent.
    //The only way to get initial commit in the results of collectChanges method
    //is to give it missing commit as fromVersion (in this case we collect changes
    //based on the date). To make handling parents easier - assign zeroId as a parent
    //version of initial commit.

    String unknownSHA = "2b9fbfbb43e7edfad018b482e15e7f93cca4e69f";
    Long firstCommitTime = 1237391915000L;
    String unknownCommit = GitUtils.makeVersion(unknownSHA, firstCommitTime - 1);

    List<ModificationData> mds = support.collectChanges(root, unknownCommit, MERGE_BRANCH_VERSION, CheckoutRules.DEFAULT);
    Map<String, String> child2parent = new HashMap<String, String>();
    for (ModificationData md : mds) {
      assertEquals(md.getParentRevisions().size(), 1);
      child2parent.put(md.getVersion(), md.getParentRevisions().get(0));
    }
    assertEquals(child2parent.get("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009"), "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7");
    assertEquals(child2parent.get("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7"), "97442a720324a0bd092fb9235f72246dc8b345bc");
    assertEquals(child2parent.get("97442a720324a0bd092fb9235f72246dc8b345bc"), "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653");
    assertEquals(child2parent.get("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653"),  "0000000000000000000000000000000000000000");
  }


  @TestFor(issues = "TW-21747")
  @Test
  public void backslash_in_username() throws VcsException {
    myConfigBuilder.setSeparateProcessForFetch(true);
    VcsRoot root = vcsRoot().withFetchUrl("ssh://domain\\user@localhost/path/to/repo.git")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withPassword("pass")
      .build();
    GitVcsSupport git = getSupport();
    try {
      git.collectChanges(root,
                         "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",//doesn't matter
                         "3b9fbfbb43e7edfad018b482e15e7f93cca4e69e",//doesn't matter
                         CheckoutRules.DEFAULT);
      fail("We try to access non-existing repository");
    } catch (VcsException e) {
      final String message = e.getMessage();
      assertNotNull(message);
      assertFalse(message.contains("domain/user"),
                  "backslash in username is replaced");
    }
  }


  @Test
  public void collect_changes_after_cache_reset() throws Exception {
    GitVcsSupport git = getSupport();
    VcsRoot root = getRoot("master");
    git.getCollectChangesPolicy().collectChanges(root, "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", CheckoutRules.DEFAULT);

    ServerPluginConfig config = myConfigBuilder.build();
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    ResetCacheHandler resetHandler = new GitResetCacheHandler(repositoryManager);
    for (String cache : resetHandler.listCaches())
      resetHandler.resetCache(cache);

    try {
      git.getCollectChangesPolicy().collectChanges(root, "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6",
                                                   "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", CheckoutRules.DEFAULT);
    } catch (VcsException e) {
      fail("Reset of caches breaks repository");
    }
  }


  @TestFor(issues = "TW-17435")
  @Test
  public void getCurrentVersion_should_not_do_fetch() throws Exception {
    ServerPluginConfig config = myConfigBuilder.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfigBuilder).withResetCacheManager(myResetCacheManager).withFetchCommand(fetchCounter).build();

    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    copyRepository(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    VcsRootImpl root = getRoot("master", false, remoteRepositoryDir);

    git.getCurrentState(root);
    assertEquals(0, fetchCounter.getFetchCount());

    git.getCurrentState(root);
    assertEquals(0, fetchCounter.getFetchCount());
  }


  @Test
  public void current_state_should_contain_revision_for_expanded_ref_in_root() throws VcsException, IOException {
    VcsRoot root = getRoot("master");
    RepositoryStateData state = getSupport().getCurrentState(root);
    final String expandedRef = GitUtils.expandRef("master");
    assertNotNull(state.getBranchRevisions().get(expandedRef));
    assertEquals(state.getDefaultBranchName(), expandedRef);
  }

  @Test
  public void test_collect_changes_between_states() throws IOException, VcsException {
    RepositoryStateData fromState = RepositoryStateData.createVersionState("master", map("master", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7"));
    RepositoryStateData toState = RepositoryStateData.createVersionState("master", map("master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
                                                        "personal-branch2", "3df61e6f11a5a9b919cb3f786a83fdd09f058617")
                                                    );
    VcsRoot root = getRoot("master", false);
    List<ModificationData> modifications = getSupport().getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    assertEquals(7, modifications.size());
  }

  @Test
  public void start_using_full_branch_name_as_default_branch_name() throws Exception {
    RepositoryStateData fromState = RepositoryStateData.createVersionState("master", map("master", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7"));
    RepositoryStateData toState = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f"));
    VcsRoot root = getRoot("master", false);
    List<ModificationData> modifications = getSupport().getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    assertEquals(4, modifications.size());
  }


  @Test
  public void should_build_patch_on_revision_in_branch_when_cache_is_empty() throws Exception {
    checkPatch("patch.non.default.branch", "master", null, "3df61e6f11a5a9b919cb3f786a83fdd09f058617", false);
  }


  @Test
  @TestFor(issues = "TW-23781")
  public void collect_changes_between_repositories_with_different_urls_and_branches() throws Exception {
    File repoGitFork = new File(myTmpDir, "repo-fork.git");
    copyRepository(myRepoGitDir, repoGitFork);

    VcsRoot root1 = vcsRoot().withFetchUrl(myMainRepositoryDir.getAbsolutePath()).withBranch("master").build();
    RepositoryStateData state1 = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9"));
    VcsRoot root2 = vcsRoot().withFetchUrl(repoGitFork.getAbsolutePath()).withBranch("patch-tests").build();
    RepositoryStateData state2 = RepositoryStateData.createVersionState("refs/heads/patch-tests", map("refs/heads/patch-tests", "27de3d118ca320d3a8a08320ff05aa0567996590"));
    List<ModificationData> changes = getSupport().getCollectChangesPolicy().collectChanges(root1, state1, root2, state2, CheckoutRules.DEFAULT);
    assertEquals(changes.size(), 11);
  }


  @Test
  @TestFor(issues = "TW-24084")
  public void should_retry_getCurrentState_if_it_fails() throws Exception {
    VcsRootImpl root = vcsRoot().withFetchUrl("ssh://some.org/repo.git")
      .withAuthMethod(AuthenticationMethod.PRIVATE_KEY_DEFAULT)
      .build();

    final FetchConnection connection = myContext.mock(FetchConnection.class);
    final Ref masterRef = myContext.mock(Ref.class, "master");
    final Ref topicRef = myContext.mock(Ref.class, "topic");
    myContext.checking(new Expectations() {{
      allowing(masterRef).getName(); will(returnValue("refs/heads/master"));
      allowing(masterRef).getObjectId(); will(returnValue(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));

      allowing(topicRef).getName(); will(returnValue("refs/heads/topic"));
      allowing(topicRef).getObjectId(); will(returnValue(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));

      allowing(connection).getRefsMap(); will(returnValue(map("refs/heads/master", masterRef, "refs/heads/topic", topicRef)));
      allowing(connection).close();
    }});

    //setup TransportFactory so that it fails to get connection several times with well known exceptions
    //and then successfully gets it on the last call
    final AtomicInteger failCount = new AtomicInteger(0);
    final List<TransportException> recoverableErrors = Arrays.asList(
      new TransportException("Session.connect: java.net.SocketException: Connection reset", new JSchException("test")),
      new TransportException("com.jcraft.jsch.JSchException: connection is closed by foreign host", new JSchException("test")),
      new TransportException("java.net.UnknownHostException: some.org", new JSchException("test")),
      new TransportException("com.jcraft.jsch.JSchException: verify: false", new JSchException("test"))
    );
    ServerPluginConfig config = myConfigBuilder.withGetConnectionRetryAttempts(recoverableErrors.size() + 1).build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    TransportFactory transportFactory = new TransportFactoryImpl(config, manager) {
      @Override
      public Transport createTransport(@NotNull Repository r, @NotNull URIish url, @NotNull AuthSettings authSettings)
        throws NotSupportedException, VcsException {
        return new Transport(r, url) {
          @Override
          public FetchConnection openFetch() throws NotSupportedException, TransportException {
            if (failCount.get() < recoverableErrors.size()) {
              TransportException error = recoverableErrors.get(failCount.get());
              failCount.incrementAndGet();
              throw error;
            } else {
              return connection;
            }
          }

          @Override
          public PushConnection openPush() throws NotSupportedException, TransportException {
            return null;
          }

          @Override
          public void close() {
          }
        };
      }
    };

    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory, new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport()
      .withPluginConfig(config)
      .withTransportFactory(transportFactory)
      .withFetchCommand(fetchCounter)
      .build();

    RepositoryStateData state = git.getCurrentState(root);
    assertEquals(state.getBranchRevisions(),
                 map("refs/heads/master", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "refs/heads/topic",  "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
  }


  @TestFor(issues = "TW-24128")
  @Test
  public void tags_in_currentState() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withBranch("master")
      .build();

    //by default - don't include tags
    RepositoryStateData state = getSupport().getCurrentState(root);
    assertFalse(state.getBranchRevisions().containsKey("refs/tags/v0.5"));
    assertFalse(state.getBranchRevisions().containsKey("refs/tags/v1.0"));

    root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withReportTags(true)
      .withBranch("master")
      .build();
    state = getSupport().getCurrentState(root);
    assertTrue(state.getBranchRevisions().containsKey("refs/tags/v0.5"));
    assertTrue(state.getBranchRevisions().containsKey("refs/tags/v1.0"));
  }


  @TestFor(issues = "TW-29778")
  @Test
  public void should_report_hash_of_commit_tag_points_to_instead_of_hash_of_tag() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withBranch("master")
      .withReportTags(true)
      .build();
    RepositoryStateData state = getSupport().getCurrentState(root);
    assertEquals("2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", state.getBranchRevisions().get("refs/tags/v0.5"));
    assertEquals("5711cbfe566b6c92e331f95d4b236483f4532eed", state.getBranchRevisions().get("refs/tags/v0.7"));
    assertEquals("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", state.getBranchRevisions().get("refs/tags/v1.0"));
  }


  @Test
  @TestFor(issues = "TW-29798")
  public void do_not_do_fetch_per_branch() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with counter
    ServerPluginConfig config = myConfigBuilder.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfigBuilder).withResetCacheManager(myResetCacheManager).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/master", state.getBranchRevisions());//has many branches

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);
  }


  @Test
  //this test should be removed if a single fetch works fine
  public void should_be_able_to_do_fetch_per_branch() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with counter
    ServerPluginConfig config = myConfigBuilder.withPerBranchFetch(true).build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfigBuilder).withResetCacheManager(myResetCacheManager).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/master", state.getBranchRevisions());//has many branches

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    assertTrue(fetchCounter.getFetchCount() > 1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    assertTrue(fetchCounter.getFetchCount() > 1);
  }


  @Test
  @TestFor(issues = "http://youtrack.jetbrains.com/issue/TW-29798#comment=27-537697")
  public void fetch_should_not_fail_if_remote_repository_does_not_have_some_branches() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myMainRepositoryDir))
      .withBranch("master")
      .withReportTags(true)
      .build();

    //setup fetcher with a counter
    ServerPluginConfig config = myConfigBuilder.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    FetchCommand fetchCommand = new FetchCommandImpl(config, new TransportFactoryImpl(config, manager), new FetcherProperties(config), manager);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    GitVcsSupport git = gitSupport().withPluginConfig(myConfigBuilder).withResetCacheManager(myResetCacheManager).withFetchCommand(fetchCounter).build();

    RepositoryStateData state = git.getCurrentState(root);
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/master", map("refs/heads/master", state.getBranchRevisions().get("refs/heads/master")));//has a single branch
    Map<String, String> branches = new HashMap<String, String>(state.getBranchRevisions());
    branches.put("refs/heads/unknown.branch", branches.get(state.getDefaultBranchName()));//unknown branch that points to a commit that exists in remote repo
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/master", branches);//has many branches, some of them don't exist in remote repository

    git.getCollectChangesPolicy().collectChanges(root, s1, s2, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);

    FileUtil.delete(config.getCachesDir());
    fetchCounter.resetFetchCounter();

    git.getCollectChangesPolicy().collectChanges(root, s2, s1, CheckoutRules.DEFAULT);
    assertEquals(fetchCounter.getFetchCount(), 1);
  }

  @Test
  public void testModificationInfoBuilderSupported() {
    Assert.assertNotNull(getSupport().getVcsExtension(ChangesInfoBuilder.class));
  }

  @Test
  public void testBuildModificationInfo() throws VcsException {
    final VcsRoot vcsRoot = getVcsRoot();

    final GitVcsSupport support = getSupport();
    final List<ChangeData> list = new ArrayList<ChangeData>();
    support.getCollectChangesPolicy().fetchAllRefs(vcsRoot);
    support.getCollectChangesPolicy().fetchChangesInfo(vcsRoot, CheckoutRules.DEFAULT, Arrays.asList("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46"), new ChangesConsumer() {
        public void consumeChange(@NotNull ChangeData change) {
          list.add(change);
        }
      }
    );

    Assert.assertEquals(list.size(), 1);
    ChangeData next = list.iterator().next();

    System.out.println("next = " + next);

    Assert.assertEquals(next.getParentRevisions(), Arrays.asList("a894d7d58ffde625019a9ecf8267f5f1d1e5c341"));
    Assert.assertEquals(next.getChanges().size(), 1);

    final VcsChange change = next.getChanges().get(0);
    Assert.assertEquals(change.getRelativeFileName(), "dir1/file3.txt");
  }

  @Test
  public void testBuildModificationInfo_parent_child() throws VcsException {
    final VcsRoot vcsRoot = getVcsRoot();

    final GitVcsSupport support = getSupport();

    final List<ChangeData> list = new ArrayList<ChangeData>();
    support.getCollectChangesPolicy().fetchAllRefs(vcsRoot);
    support.getCollectChangesPolicy().fetchChangesInfo(vcsRoot, CheckoutRules.DEFAULT, Arrays.asList("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", "a894d7d58ffde625019a9ecf8267f5f1d1e5c341"), new ChangesConsumer() {
                                                         public void consumeChange(@NotNull ChangeData change) {
                                                           list.add(change);
                                                         }
                                                       }
    );

    Assert.assertEquals(list.size(), 2);

    for (ChangeData data : list) {
      final String commitId = data.getVersion();

      if (commitId.equals("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46")) {
        Assert.assertEquals(data.getParentRevisions(), Arrays.asList("a894d7d58ffde625019a9ecf8267f5f1d1e5c341"));
      } else if (commitId.equals("a894d7d58ffde625019a9ecf8267f5f1d1e5c341")) {
        Assert.assertEquals(data.getParentRevisions(), Arrays.asList("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653"));
      } else {
        Assert.fail("Unexpected commit: " + commitId);
      }
    }
  }

  @Test
  public void testBuildModificationInfo_parent_gap_child() throws VcsException {
    final VcsRoot vcsRoot = getVcsRoot();

    final GitVcsSupport support = getSupport();

    final List<ChangeData> list = new ArrayList<ChangeData>();
    support.getCollectChangesPolicy().fetchAllRefs(vcsRoot);
    support.getCollectChangesPolicy().fetchChangesInfo(vcsRoot, CheckoutRules.DEFAULT, Arrays.asList("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653"), new ChangesConsumer() {
                                                         public void consumeChange(@NotNull ChangeData change) {
                                                           list.add(change);
                                                         }
                                                       }
    );

    Assert.assertEquals(list.size(), 2);
  }

  @Test
  public void testBuildModificationInfo_MergeCommit() throws VcsException {
    final VcsRoot vcsRoot = getVcsRoot();

    final GitVcsSupport support = getSupport();
    final List<ChangeData> list = new ArrayList<ChangeData>();
    support.getCollectChangesPolicy().fetchAllRefs(vcsRoot);
    support.getCollectChangesPolicy().fetchChangesInfo(vcsRoot, CheckoutRules.DEFAULT, Arrays.asList("f3f826ce85d6dad25156b2d7550cedeb1a422f4c"), new ChangesConsumer() {
                                                         public void consumeChange(@NotNull ChangeData change) {
                                                           list.add(change);
                                                         }
                                                       }
    );

    Assert.assertEquals(list.size(), 1);
    ChangeData next = list.iterator().next();

    System.out.println("next = " + next);

    Assert.assertEquals(new HashSet<String>(next.getParentRevisions()), new HashSet<String>(Arrays.asList("6fce8fe45550eb72796704a919dad68dc44be44a", "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009")));
    Assert.assertEquals(next.getChanges().size(), 3);
  }

  private static class FetchCommandCountDecorator implements FetchCommand {

    private final FetchCommand myDelegate;
    private int myFetchCount = 0;

    FetchCommandCountDecorator(FetchCommand delegate) {
      myDelegate = delegate;
    }

    public void fetch(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull Collection<RefSpec> refspecs, @NotNull AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
      myDelegate.fetch(db, fetchURI, refspecs, auth);
      inc();
    }

    private synchronized void inc() {
      myFetchCount++;
    }

    public synchronized int getFetchCount() {
      return myFetchCount;
    }

    public synchronized void resetFetchCounter() {
      myFetchCount = 0;
    }
  }


  @Test
  @TestFor(issues = "TW-29770")
  public void collect_changes_with_branch_pointing_to_a_non_commit() throws Exception {
    //setup remote repo with a branch pointing to a non commit
    VcsRoot root = vcsRoot().withBranch("refs/heads/master").withFetchUrl(myMainRepositoryDir.getAbsolutePath()).build();

    File brokenRef = new File(myMainRepositoryDir, "refs" + File.separator + "heads" + File.separator + "broken_branch");
    FileUtil.writeFileAndReportErrors(brokenRef, "1fefad14fba39ac378e4e345e295fa1f90e343ae\n");//it's a tree, not a commit

    RepositoryStateData state1 = createVersionState("refs/heads/master",
                                                    map("refs/heads/master", "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6",
                                                        "refs/heads/broken_branch", "1fefad14fba39ac378e4e345e295fa1f90e343ae"));
    RepositoryStateData state2 = createVersionState("refs/heads/master",
                                                    map("refs/heads/master", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
                                                        "refs/heads/broken_branch", "1fefad14fba39ac378e4e345e295fa1f90e343ae"));

    //collect changes in this repo, no exception should be thrown
    getSupport().getCollectChangesPolicy().collectChanges(root, state1, state2, CheckoutRules.DEFAULT);
  }


  private File createBranchLockFile(File repositoryDir, String branch) throws IOException {
    String branchRefPath = "refs" + File.separator + "heads" + File.separator + branch;
    File refFile  = new File(repositoryDir, branchRefPath);
    File refLockFile = new File(repositoryDir, branchRefPath + ".lock");
    FileUtil.copy(refFile, refLockFile);
    return refLockFile;
  }


  private String getFetchExceptionMessage() {
    String result = null;
    File notExisting = new File(myTmpDir, "not-existing");
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(notExisting));
    try {
      getSupport().collectChanges(root, MERGE_VERSION, AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION, CheckoutRules.DEFAULT);
      fail("Should throw an exception for not-existing repository");
    } catch (VcsException e) {
      result = e.getMessage();
    }
    return result;
  }
}
