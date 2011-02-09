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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.patches.PatchTestCase;
import org.apache.log4j.Level;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
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

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

/**
 * The tests for version detection functionality
 */
public class GitVcsSupportTest extends PatchTestCase {
  /**
   * The version of "version-test" HEAD
   */
  public static final String VERSION_TEST_HEAD = GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915000L);
  /**
   * The version that contains add/remove/update changes
   */
  public static final String CUD1_VERSION = GitUtils.makeVersion("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", 1238072086000L);
  /**
   * The merge head version
   */
  private static final String MERGE_VERSION = GitUtils.makeVersion("f3f826ce85d6dad25156b2d7550cedeb1a422f4c", 1238086450000L);
  /**
   * The merge branch version
   */
  private static final String MERGE_BRANCH_VERSION = GitUtils.makeVersion("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", 1238085489000L);
  /**
   * The merge branch version
   */
  public static final String SUBMODULE_MODIFIED_VERSION = GitUtils.makeVersion("37c371a6db0acefc77e3be99d16a44413e746591", 1245773817000L);
  /**
   * The merge branch version
   */
  public static final String SUBMODULE_ADDED_VERSION = GitUtils.makeVersion("b5d65401a4e8a09b80b8d73ca4392f1913e99ff5", 1245766034000L);
  /**
   * The merge branch version
   */
  public static final String SUBMODULE_TXT_ADDED_VERSION = GitUtils.makeVersion("d1a88fd33c516c1b607db75eb62244b2ea495c42", 1246534153000L);
  /**
   * The merge branch version
   */
  public static final String BEFORE_SUBMODULE_ADDED_VERSION =
    GitUtils.makeVersion("592c5bcee6d906482177a62a6a44efa0cff9bbc7", 1238421437000L);
  /**
   * Version before submodule which itselft contains submodules added
   */
  public static final String BEFORE_FIRST_LEVEL_SUBMODULE_ADDED_VERSION =
    GitUtils.makeVersion("f3f826ce85d6dad25156b2d7550cedeb1a422f4c", 1238421437000L);
  /**
   * Version after submodule which itself contains submodules added
   */
  public static final String AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION =
    GitUtils.makeVersion("ce6044093939bb47283439d97a1c80f759669ff5", 1238421437000L);
  /**
   * The source directory
   */
  protected File myMainRepositoryDir;
  private File myTmpDir;
  private ServerPaths myServerPaths;
  /**
   * Temporary files
   */
  protected static TempFiles myTempFiles = new TempFiles();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        myTempFiles.cleanup();
      }
    }));
  }

  @BeforeMethod
  public void setUp() throws IOException {
    File teamcitySystemDir = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath());
    File masterRep = dataFile("repo.git");
    myTmpDir = myTempFiles.createTempDir();
    myMainRepositoryDir = new File(myTmpDir, "repo.git");
    FileUtil.copyDir(masterRep, myMainRepositoryDir);
    FileUtil.copyDir(dataFile("submodule.git"), new File(myTmpDir, "submodule"));
    FileUtil.copyDir(dataFile("submodule.git"), new File(myTmpDir, "submodule.git"));
    FileUtil.copyDir(dataFile("sub-submodule.git"), new File(myTmpDir, "sub-submodule.git"));
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
    VcsRootImpl myRoot = new VcsRootImpl(1, Constants.VCS_NAME);
    myRoot.addProperty(Constants.FETCH_URL, GitUtils.toURL(myMainRepositoryDir));
    if (branchName != null) {
      myRoot.addProperty(Constants.BRANCH_NAME, branchName);
    }
    if (enableSubmodules) {
      myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    }
    return myRoot;
  }

  private GitVcsSupport getSupport() {
    return getSupport(null);
  }

  private GitVcsSupport getSupport(ExtensionHolder holder) {
    return new GitVcsSupport(myServerPaths, holder, null);
  }


  protected String getTestDataPath() {
    return dataFile().getPath();
  }

  /**
   * The connection test
   *
   * @throws Exception in case of IO problem
   */
  @Test
  public void testConnection() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("version-test");
    support.testConnection(root);
    try {
      root = getRoot("no-such-branch");
      support.testConnection(root);
      fail("The connection should have failed");
    } catch (VcsException ex) {
      // test successful
    }
  }

  /**
   * The current version test
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCurrentVersion(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("version-test");
    String version = support.getCurrentVersion(root);
    assertEquals(VERSION_TEST_HEAD, version);
  }

  /**
   * Test get content for the file
   *
   * @throws Exception in case of bug
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testGetContent(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("version-test");
    String version = support.getCurrentVersion(root);
    byte[] data1 = support.getContent("readme.txt", root, version);
    byte[] data2 = FileUtil.loadFileBytes(dataFile("content", "readme.txt"));
    assertEquals(data1, data2);
    try {
      support.getContent("non-existing file.txt", root, version);
      fail("The file must not be loaded");
    } catch (VcsFileNotFoundException ex) {
      // ignore exception
    }
  }

  /**
   * Test get content for the file
   *
   * @throws Exception in case of bug
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testGetContentSubmodules(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("patch-tests", true);
    String version = support.getCurrentVersion(root);
    byte[] data1 = support.getContent("submodule/file.txt", root, version);
    byte[] data2 = FileUtil.loadFileBytes(dataFile("content", "submodule file.txt"));
    assertEquals(data1, data2);
  }


  /**
   * Test getting changes for the build
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChanges(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
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
    String missing = GitUtils.makeVersion(GitUtils.versionRevision(CUD1_VERSION).replace('0', 'f'), GitUtils.versionTime(CUD1_VERSION));
    final List<ModificationData> mms2 = support.collectChanges(root, missing, MERGE_VERSION, new CheckoutRules(""));
    assertEquals(4, mms2.size());
    ModificationData mb3 = mms2.get(3);
    assertEquals(GitServerUtil.SYSTEM_USER, mb3.getUserName());
    assertEquals(0, mb3.getChanges().size());
  }

  /**
   * Test collecting build changes respects checkout rules
   *    master
   *      o 2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6 (merge commit with no changed files)
   *     /|
   *    / |
   *   o 2494559261ab85e92b1780860b34f876b5e6bce6 (commit from other branch) changes file readme.txt (not in dir/)
   *      |
   *      |
   *      o 3b9fbfbb43e7edfad018b482e15e7f93cca4e69f (no changes in dir/)
   */
  @Test
  public void testCollectBuildChangesWithCheckoutRules() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");
    final List<ModificationData> mds = support.collectChanges(root,
                                                              GitUtils.makeVersion("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f", 1283497225000L),
                                                              GitUtils.makeVersion("2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", 1286183770000L),
                                                              new CheckoutRules("+:dir=>."));
    //we can ignore checkout rules during collecting changes, TeamCity will apply them later,
    //but we should not set canBeIgnored = false for modifications, otherwise TeamCity won't exclude them
    for (ModificationData md : mds) {
      assertTrue(md.isCanBeIgnored());
    }
  }

  @Test
  public void testMergeCommitCannotBeIgnored() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");
    final List<ModificationData> mds = support.collectChanges(root,
                                                              GitUtils.makeVersion("2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6", 1289483376000L),
                                                              GitUtils.makeVersion("465ad9f630e451b9f2b782ffb09804c6a98c4bb9", 1289483394000L),
                                                              new CheckoutRules("+:dir=>."));
    ModificationData mergeCommit = mds.get(0);
    assertFalse(mergeCommit.isCanBeIgnored());
  }

  /**
   * Test getting changes for the build concurrently. Copy of previous test but with several threads collecting changes
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testConcurrentCollectBuildChanges(boolean fetchInSeparateProcess) throws Throwable {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));

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
          String missing = GitUtils.makeVersion(GitUtils.versionRevision(CUD1_VERSION).replace('0', 'f'), GitUtils.versionTime(CUD1_VERSION));
          final List<ModificationData> mms2 = support.collectChanges(root, missing, MERGE_VERSION, new CheckoutRules(""));
          assertEquals(4, mms2.size());
          ModificationData mb3 = mms2.get(3);
          assertEquals(GitServerUtil.SYSTEM_USER, mb3.getUserName());
          assertEquals(0, mb3.getChanges().size());
        } catch (Throwable e) {
          errors.add(e);
        }
      }
    };

    for (int i = 0; i < 50; i++) {
      BaseTestCase.runAsyncAndFailOnException(4, r1, r2, r3, r4);
    }

    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }


  /**
   * Test getting changes for the build with submodules ignored
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubmodulesIgnored(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
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

  /**
   * Test getting changes for the build
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubmodules(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
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


  /**
   * TW-13127
   *
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
  @Test
  public void testCollectBuildChangesWithBrokenSubmoduleOnLastCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);

    String brokenSubmoduleCommit = GitUtils.makeVersion("78cbbed3561de3417467ee819b1795ba14c03dfb", 1282637672000L);
    try {
      support.collectChanges(root, MERGE_VERSION, brokenSubmoduleCommit, new CheckoutRules(""));
      fail("We should throw exception if submodules in the last commit are broken");
    } catch (Exception e) {
      assertTrue(true);
    }
  }


  @Test
  public void testCollectBuildChangesWithFixedSubmoduleOnLastCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);

    String fixedSubmoduleCommit = GitUtils.makeVersion("f5bdd3819df0358a43d9a8f94eaf96bb306e19fe", 1282636308000L);
    List<ModificationData> mds = support.collectChanges(root, MERGE_VERSION, fixedSubmoduleCommit, new CheckoutRules(""));
    assertEquals(mds.size(), 4);
    assertEquals(mds.get(0).getChanges().size(), 2);
  }


  @Test
  public void testCollectBuildChangesWithFixedBrokenFixedSubmodule() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fixedSubmoduleCommit = GitUtils.makeVersion("f5bdd3819df0358a43d9a8f94eaf96bb306e19fe", 1282636308000L);
    String submoduleFixedAgainCommit = GitUtils.makeVersion("92112555d9eb3e433eaa91fe32ec001ae8fe3c52", 1282736040000L);
    List<ModificationData> mds = support.collectChanges(root, fixedSubmoduleCommit, submoduleFixedAgainCommit, new CheckoutRules(""));
    assertEquals(2, mds.size());

    for (ModificationData md : mds) {
      assertEquals(md.getChanges().size(), 1); //this means we don't report remove and add of all submodule files
    }
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
    List<ModificationData> mds = support.collectChanges(root, beforeSubmodWithDirCommit, submodWithDirCommit, new CheckoutRules(""));
  }


  /**
   * Test collecting changes with non-recursive submodule checkout: only first level submodule files are checked out
   *
   * @param fetchInSeparateProcess
   * @throws Exception
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubSubmodulesNonRecursive(boolean fetchInSeparateProcess) throws Exception {
    checkCollectBuildChangesSubSubmodules(fetchInSeparateProcess, false);
  }


  /**
   * Test collecting changes with recursive submodule checkout: submodules of submodules are checked out
   *
   * @param fetchInSeparateProcess
   * @throws Exception
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCollectBuildChangesSubSubmodulesRecursive(boolean fetchInSeparateProcess) throws Exception {
    checkCollectBuildChangesSubSubmodules(false, true);
  }


  private void checkCollectBuildChangesSubSubmodules(boolean fetchInSeparateProcess, boolean recursiveSubmoduleCheckout)
    throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));

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


  /**
   * Test patches
   *
   * @throws IOException  in case of test failure
   * @throws VcsException in case of test failure
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testPatches(boolean fetchInSeparateProcess) throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    checkPatch("cleanPatch1", null, GitUtils.makeVersion("a894d7d58ffde625019a9ecf8267f5f1d1e5c341", 1237391915000L));
    checkPatch("patch1", GitUtils.makeVersion("70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46", 1238420977000L),
               GitUtils.makeVersion("0dd03338d20d2e8068fbac9f24899d45d443df38", 1238421020000L));
    checkPatch("patch2", GitUtils.makeVersion("7e916b0edd394d0fca76456af89f4ff7f7f65049", 1238421159000L),
               GitUtils.makeVersion("049a98762a29677da352405b27b3d910cb94eb3b", 1238421214000L));
    checkPatch("patch3", null, GitUtils.makeVersion("1837cf38309496165054af8bf7d62a9fe8997202", 1238421349000L));
    checkPatch("patch4", GitUtils.makeVersion("1837cf38309496165054af8bf7d62a9fe8997202", 1238421349000L),
               GitUtils.makeVersion("592c5bcee6d906482177a62a6a44efa0cff9bbc7", 1238421437000L));
    checkPatch("patch-case", "rename-test", GitUtils.makeVersion("cbf1073bd3f938e7d7d85718dbc6c3bee10360d9", 1247581634000L),
               GitUtils.makeVersion("2eed4ae6732536f76a65136a606f635e8ada63b9", 1247581803000L), true);
  }

  /**
   * Test patches
   *
   * @throws IOException  in case of test failure
   * @throws VcsException in case of test failure
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testSubmodulePatches(boolean fetchInSeparateProcess) throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    checkPatch("submodule-added-ignore", BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION);
    checkPatch("submodule-removed-ignore", SUBMODULE_ADDED_VERSION, BEFORE_SUBMODULE_ADDED_VERSION);
    checkPatch("submodule-modified-ignore", SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION);
    checkPatch("submodule-added", "patch-tests", BEFORE_SUBMODULE_ADDED_VERSION, SUBMODULE_ADDED_VERSION, true);
    checkPatch("submodule-removed", "patch-tests", SUBMODULE_ADDED_VERSION, BEFORE_SUBMODULE_ADDED_VERSION, true);
    checkPatch("submodule-modified", "patch-tests", SUBMODULE_ADDED_VERSION, SUBMODULE_MODIFIED_VERSION, true);
  }


  /**
   * Check single patch
   *
   * @param name        the name of patch
   * @param fromVersion from version
   * @param toVersion   to version
   * @throws IOException  in case of test failure
   * @throws VcsException in case of test failure
   */
  private void checkPatch(final String name, final String fromVersion, final String toVersion) throws IOException, VcsException {
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
  private void checkPatch(final String name,
                          final String branchName, final String fromVersion,
                          final String toVersion,
                          boolean enableSubmodules
  )
    throws IOException, VcsException {
    setName(name);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot(branchName, enableSubmodules);
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final PatchBuilderImpl builder = new PatchBuilderImpl(output);
    support.buildPatch(root, fromVersion, toVersion, builder, new CheckoutRules(""));
    builder.close();
    checkPatchResult(output.toByteArray());
  }

  /**
   * Test label implementation
   *
   * @throws IOException        in case of test failure
   * @throws VcsException       in case of test failure
   * @throws URISyntaxException in case of test failure
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testLabels(boolean fetchInSeparateProcess) throws IOException, VcsException, URISyntaxException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master");
    // ensure that all revisions reachable from master are fetched
    support.label("test_label", VERSION_TEST_HEAD, root, new CheckoutRules(""));
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

  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testMapFullPath(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
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
          for (int i = 0; i < 5; i++) {
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(VERSION_TEST_HEAD) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(MERGE_VERSION) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(MERGE_BRANCH_VERSION) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(CUD1_VERSION) + "|" + repositoryUrl + "|readme.txt");
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


  /**
   * TW-13330
   * Test reproduces bug in Fetcher code: Fetcher worked only if all parameters of VcsRoot
   * sent to process input as string were smaller than 512 bytes (most of the cases) or size mod 512 = 0.
   */
  @Test
  public void test_long_input_for_fetcher_process() throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", "true");
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
    String version = support.getCurrentVersion(root);
    assertEquals(VERSION_TEST_HEAD, version);
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
    assertEquals(jetbrainsPlugin.getDisplayName(), "Git (Jetbrains plugin)");
  }


  @Test
  public void test_display_name_no_conflict() {
    Mockery context = new Mockery();
    final ExtensionHolder holder = context.mock(ExtensionHolder.class);
    final VcsSupportContext anotherVcsPlugin = context.mock(VcsSupportContext.class);
    final VcsSupportCore anotherVcsPluginCore = context.mock(VcsSupportCore.class);
    context.checking(new Expectations() {{
      allowing(holder).getServices(VcsSupportContext.class); will(returnValue(Arrays.asList(anotherVcsPlugin)));
      allowing(anotherVcsPlugin).getCore(); will(returnValue(anotherVcsPluginCore));
      allowing(anotherVcsPluginCore).getName(); will(returnValue("hg"));
    }});
    GitVcsSupport jetbrainsPlugin = getSupport(holder);
    assertEquals(jetbrainsPlugin.getDisplayName(), "Git");
  }


  /**
   * Test work-around for http://youtrack.jetbrains.net/issue/TW-9933.
   */
  @Test
  public void test_not_existing_local_repository() {
    File notExisting = new File(myTmpDir, "not-existing");
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(notExisting));
    try {
      getSupport().testConnection(root);
      fail("Should throw an exception for not-existing repository");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Cannot access repository"));
      assertFalse(e.getMessage().endsWith("\n"));
    }
  }


  /**
   * TW-14813
   */
  @Test
  public void test_logging() {
    System.setProperty("teamcity.git.fetch.separate.process", "true");

    String noDebugError = getCurrentVersionExceptionMessage();
    assertFalse(noDebugError.contains("at jetbrains.buildServer.buildTriggers.vcs.git.Fetcher"));//no stacktrace
    assertFalse(noDebugError.endsWith("\n"));

    Loggers.VCS.setLevel(Level.DEBUG);
    String debugError = getCurrentVersionExceptionMessage();
    assertTrue(debugError.contains("at jetbrains.buildServer.buildTriggers.vcs.git.Fetcher"));
    assertFalse(debugError.endsWith("\n"));
  }


  private String getCurrentVersionExceptionMessage() {
    String result = null;
    File notExisting = new File(myTmpDir, "not-existing");
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(notExisting));
    try {
      getSupport().getCurrentVersion(root);
      fail("Should throw an exception for not-existing repository");
    } catch (VcsException e) {
      result = e.getMessage();
    }
    return result;
  }
}
