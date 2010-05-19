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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.patches.PatchTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Tag;
import org.eclipse.jgit.transport.URIish;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

/**
 * The tests for version detection functionality
 */
public class GitVcsSupportTest extends PatchTestCase {
  /**
   * The version of "version-test" HEAD
   */
  private static final String VERSION_TEST_HEAD = GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915000L);
  /**
   * The version that contains add/remove/update changes
   */
  private static final String CUD1_VERSION = GitUtils.makeVersion("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", 1238072086000L);
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
   * The source directory
   */
  protected File mySourceRep;
  /**
   * The caches directory
   */
  private File myCachesDir;
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
    myRoot.addProperty(Constants.FETCH_URL, GitUtils.toURL(mySourceRep));
    if (branchName != null) {
      myRoot.addProperty(Constants.BRANCH_NAME, branchName);
      myRoot.addProperty(Constants.SUBMODULE_URLS, "submodule\n" + GitUtils.toURL(dataFile("submodule.git")));
    }
    if (enableSubmodules) {
      myRoot.addProperty(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    }
    return myRoot;
  }

  /**
   * @return a created vcs support object
   */
  protected GitVcsSupport getSupport() {
    return new GitVcsSupport(null) {
      @Override
      protected Settings createSettings(VcsRoot vcsRoot) throws VcsException {
        final Settings s = super.createSettings(vcsRoot);
        s.setCachesDirectory(myCachesDir.getPath());
        return s;
      }
    };
  }

  /**
   * Setup test environment
   *
   * @throws IOException in case of IO problem
   */
  @BeforeMethod
  public void setUp() throws IOException {
    File masterRep = dataFile("repo.git");
    mySourceRep = myTempFiles.createTempDir();
    FileUtil.copyDir(masterRep, mySourceRep);
    myCachesDir = myTempFiles.createTempDir();
  }

  /**
   * {@inheritDoc}
   */
  protected String getTestDataPath() {
    return dataFile().getPath();
  }


  /**
   * Tear down test environment
   */
  @AfterMethod
  public void tearDown() {
    // clear root
    myTempFiles.cleanup();
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
      Assert.fail("The connection should have failed");
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
    Assert.assertEquals(VERSION_TEST_HEAD, version);
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
    Assert.assertEquals(data1, data2);
    try {
      support.getContent("non-existing file.txt", root, version);
      Assert.fail("The file must not be loaded");
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
    Assert.assertEquals(data1, data2);
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
    assertFalse(md1.isCanBeIgnored());
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
   * Test getting changes for the build concurrently. Copy of previous test but with several threads collecting changes
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testConcurrentCollectBuildChanges(boolean fetchInSeparateProcess) throws Exception {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));

    final GitVcsSupport support = getSupport();
    final List<Exception> errors = Collections.synchronizedList(new ArrayList<Exception>());

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
        } catch (Exception e) {
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
        } catch (Exception e) {
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
          assertFalse(md1.isCanBeIgnored());
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
        } catch (Exception e) {
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
        } catch (Exception e) {
          errors.add(e);
        }
      }
    };

    for (int i = 0; i < 50; i++) {
      BaseTestCase.runAsync(4, r1, r2, r3, r4);
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
    Repository r = new Repository(new File(new URIish(root.getProperty(Constants.FETCH_URL)).getPath()));
    try {
      Tag t = r.mapTag("test_label");
      assertEquals(t.getObjId().name(), GitUtils.versionRevision(VERSION_TEST_HEAD));
    } finally {
      r.close();
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
}
