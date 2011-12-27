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
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.patches.PatchTestCase;
import org.apache.log4j.Level;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
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

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.util.FileUtil.writeFile;

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
  private PluginConfigBuilder myConfigBuilder;
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
    myConfigBuilder = new PluginConfigBuilder(myServerPaths);
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
    return getRoot(branchName, enableSubmodules, myMainRepositoryDir);
  }

  private VcsRootImpl getRoot(String branchName, boolean enableSubmodules, File repositoryDir) {
    VcsRootImpl myRoot = new VcsRootImpl(1, Constants.VCS_NAME);
    myRoot.addProperty(Constants.FETCH_URL, GitUtils.toURL(repositoryDir));
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
    ServerPluginConfig config = myConfigBuilder.build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    return new GitVcsSupport(config, transportFactory, fetchCommand, repositoryManager, holder);
  }


  protected String getTestDataPath() {
    return dataFile().getPath();
  }


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
      assertTrue(true);
    }
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


  @Test
  public void testConnection_should_throw_exception_for_anonymous_git_url_with_username() throws Exception {
    String url = "git://git@some.org/repository";
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, url);
    try {
      getSupport().testConnection(root);
      fail("should fail, because native git fails for such url");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Incorrect url " + url + ": anonymous git url should not contain a username"));
    }

    //other operations should fail with another error message,
    //that means old roots that have such urls and use server-side checkout will still work
    try {
      getSupport().collectChanges(root, MERGE_VERSION, AFTER_FIRST_LEVEL_SUBMODULE_ADDED_VERSION, CheckoutRules.DEFAULT);
      fail("should fail, because no such root exists");
    } catch (VcsException e) {
      assertFalse(e.getMessage().contains("Incorrect url " + url + ": anonymous git url should not contain a username"));
    }
  }


  /**
   * The current version test
   *
   * @throws Exception in case of IO problem
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testCurrentVersion(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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

  @Test
  public void merge_commit_with_interesting_changes_cannot_be_ignored() throws Exception {
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);

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

    BaseTestCase.runAsyncAndFailOnException(4, r1, r2, r3, r4);

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

  /**
   * Test getting changes for the build
   *
   * @throws Exception in case of IO problem
   */
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


  //TW-19544
  @Test
  public void testCollectChangesWithBrokenSubmoduleOnLastCommitAndUsualFileInsteadOfSubmoduleInPreviousCommit() throws Exception {
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fromCommit = GitUtils.makeVersion("f5bdd3819df0358a43d9a8f94eaf96bb306e19fe", 1282636308000L);
    String lastCommit = GitUtils.makeVersion("39679cc440c83671fbf6ad8083d92517f9602300", 1324998585000L);
    support.collectChanges(root, fromCommit, lastCommit, CheckoutRules.DEFAULT);
  }


  @Test
  public void should_not_traverse_history_deeper_than_specified_limit() throws Exception {
    myConfigBuilder.setFixedSubmoduleCommitSearchDepth(0);//do no search submodule commit with fix at all
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("wrong-submodule", true);
    String fixedSubmoduleCommit = GitUtils.makeVersion("f5bdd3819df0358a43d9a8f94eaf96bb306e19fe", 1282636308000L);
    String submoduleFixedAgainCommit = GitUtils.makeVersion("92112555d9eb3e433eaa91fe32ec001ae8fe3c52", 1282736040000L);
    List<ModificationData> mds = support.collectChanges(root, fixedSubmoduleCommit, submoduleFixedAgainCommit, new CheckoutRules(""));
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

    for (VcsChange change : mds.get(0).getChanges()) {
      assertTrue(change.getBeforeChangeRevisionNumber().contains("@"), "revision should contain time");
    }
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


  /**
   * Test patches
   *
   * @throws IOException  in case of test failure
   * @throws VcsException in case of test failure
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testPatches(boolean fetchInSeparateProcess) throws IOException, VcsException {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
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
          for (int i = 0; i < 5; i++) {
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(VERSION_TEST_HEAD) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                "ad4528ed5c84092fdbe9e0502163cf8d6e6141e8|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(MERGE_VERSION) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                "ad4528ed5c84092fdbe9e0502163cf8d6e6141e9|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(MERGE_BRANCH_VERSION) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                "ad4528ed5c84092fdbe9e0502163cf8d6e6141f0|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                GitUtils.versionRevision(CUD1_VERSION) + "|" + repositoryUrl + "|readme.txt");
            support.mapFullPath(new VcsRootEntry(root, new CheckoutRules("")),
                                "ad4528ed5c84092fdbe9e0502163cf8d6e6141f1|" + repositoryUrl + "|readme.txt");
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


  /**
   * TW-14813
   */
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


  /**
   * TW-15564: repository cloned by hand could have no teamcity.remote config, we should create it otherwise we can see 'null' as remote url in error messages
   * (see log in the issue for details).
   */
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void should_create_teamcity_config_in_root_with_custom_path(boolean fetchInSeparateProcess) throws IOException, VcsException {
    System.setProperty("teamcity.git.fetch.separate.process", String.valueOf(fetchInSeparateProcess));
    File customRootDir = new File(myTmpDir, "custom-dir");
    VcsRootImpl root = (VcsRootImpl) getRoot("master");
    root.addProperty(Constants.PATH, customRootDir.getAbsolutePath());
    getSupport().getCurrentVersion(root);

    File configFile = new File(customRootDir, "config");
    String config = FileUtil.readText(configFile);
    Pattern pattern = Pattern.compile("(.*)\\[teamcity\\]\\s+remote = " + Pattern.quote(GitUtils.toURL(myMainRepositoryDir)) + "\\s*(.*)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(config);
    assertTrue(matcher.matches(), "config is " + config);

    //erase teamcity.remote config
    String newConfig = matcher.group(1) + matcher.group(2);
    writeFile(configFile, newConfig);

    getSupport().getCurrentVersion(root);
    config = FileUtil.readText(configFile);
    assertTrue(pattern.matcher(config).matches());
  }


  @Test
  public void should_support_git_refs_format() throws IOException, VcsException {
    String versionTest1 = getSupport().getCurrentVersion(getRoot("version-test"));
    String versionTest2 = getSupport().getCurrentVersion(getRoot("refs/heads/version-test"));
    assertEquals(versionTest1, versionTest2);
    String master1 = getSupport().getCurrentVersion(getRoot("master"));
    String master2 = getSupport().getCurrentVersion(getRoot("refs/heads/master"));
    String master3 = getSupport().getCurrentVersion(getRoot("refs/tags/v1.0"));
    assertEquals(master1, master2);
    assertEquals(master1, master3);
  }


  //TW-17910
  @Test
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


  //TW-16351
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void should_update_local_ref_when_it_locked(boolean fetchInSeparateProcess) throws Exception {
    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    FileUtil.copyDir(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRootImpl root = getRoot("master", false, remoteRepositoryDir);
    String branch = root.getProperty(Constants.BRANCH_NAME);
    File customRootDir = new File(myTmpDir, "custom-dir");
    root.addProperty(Constants.PATH, customRootDir.getAbsolutePath());

    String v1 = GitUtils.versionRevision(support.getCurrentVersion(root));

    FileUtil.copyDir(dataFile("repo_for_fetch.2"), remoteRepositoryDir);//now remote repository contains new commits

    File branchLockFile = createBranchLockFile(customRootDir, branch);
    assertTrue(branchLockFile.exists());

    String v2 = GitUtils.versionRevision(support.getCurrentVersion(root));
    assertFalse(v2.equals(v1));//local repository is updated
  }


  //TW-16351
  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void test_non_fast_forward_update(boolean fetchInSeparateProcess) throws Exception {
    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    FileUtil.copyDir(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport support = getSupport();
    VcsRoot root = getRoot("master", false, remoteRepositoryDir);

    String v1 = GitUtils.versionRevision(support.getCurrentVersion(root));
    assertEquals(v1, "add81050184d3c818560bdd8839f50024c188586");

    FileUtil.copyDir(dataFile("repo_for_fetch.2"), remoteRepositoryDir);//fast-forward update

    String v2 = GitUtils.versionRevision(support.getCurrentVersion(root));
    assertEquals(v2, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168");

    FileUtil.copyDir(dataFile("repo_for_fetch.3"), remoteRepositoryDir);//non-fast-forward update
    String v3 = GitUtils.versionRevision(support.getCurrentVersion(root));
    assertEquals("bba7fbcc200b4968e6abd2f7d475dc15306cafc6", v3);
  }


  @Test
  public void test_getPersonalBranchDescription_when_branch_contains_commits() throws Exception {
    VcsRoot root = getRoot("master");
    GitVcsSupport support = getSupport();
    PersonalBranchDescription description = support.getPersonalBranchDescription(root, "personal-branch2");
    assertNotNull(description);
    assertEquals(description.getBranchId(), "1391281d33a83a7205f2f05d3eb64c349c636e87");
    assertEquals(description.getUsername(), "other.user");
  }


  @Test
  public void test_getPersonalBranchDescription_when_branch_does_not_contain_commits() throws Exception {
    VcsRoot root = getRoot("master");
    GitVcsSupport support = getSupport();
    PersonalBranchDescription description = support.getPersonalBranchDescription(root, "master");
    assertNull(description);
  }


  //TW-16530
//  @Test
  public void test_crlf() throws Exception {
    String original = System.getProperty("user.home");
    try {
      File homeDir = myTempFiles.createTempDir();
      File userConfig = new File(homeDir, ".gitconfig");
      FileUtil.writeFile(userConfig, "[core]\n autocrlf=true\n");
      System.setProperty("user.home", homeDir.getAbsolutePath());

      VcsRoot root = getRoot("master");
      byte[] bytes = getSupport().getContent("readme.txt", root, "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f@1283497225000");
      String content = new String(bytes);
      assertTrue(content.contains("\r\n"));
    } finally {
      System.setProperty("user.home", original);
    }
  }


  //TW-17435
  //@Test
  public void getCurrentVersion_should_not_do_fetch_if_remote_ref_not_changed() throws Exception {
    ServerPluginConfig config = myConfigBuilder.build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    FetchCommandCountDecorator fetchCounter = new FetchCommandCountDecorator(fetchCommand);
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    GitVcsSupport git = new GitVcsSupport(config, transportFactory, fetchCounter, repositoryManager, null);

    File remoteRepositoryDir = new File(myTmpDir, "repo_for_fetch");
    FileUtil.copyDir(dataFile("repo_for_fetch.1"), remoteRepositoryDir);

    VcsRootImpl root = getRoot("master", false, remoteRepositoryDir);

    GitUtils.versionRevision(git.getCurrentVersion(root));
    assertEquals(1, fetchCounter.getFetchCount());

    GitUtils.versionRevision(git.getCurrentVersion(root));
    assertEquals(1, fetchCounter.getFetchCount());
  }


  private static class FetchCommandCountDecorator implements FetchCommand {

    private final FetchCommand myDelegate;
    private int myFetchCount = 0;

    FetchCommandCountDecorator(FetchCommand delegate) {
      myDelegate = delegate;
    }

    public void fetch(@NotNull Repository db, URIish fetchURI, Collection<RefSpec> refspecs, Settings.AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
      myDelegate.fetch(db, fetchURI, refspecs, auth);
      inc();
    }

    private synchronized void inc() {
      myFetchCount++;
    }

    public synchronized int getFetchCount() {
      return myFetchCount;
    }
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
