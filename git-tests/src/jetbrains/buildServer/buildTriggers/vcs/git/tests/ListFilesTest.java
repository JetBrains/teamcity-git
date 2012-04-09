/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.ListFilesTest.VcsFileDataMatcher.vcsDir;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.ListFilesTest.VcsFileDataMatcher.vcsFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionContaining.hasItems;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author dmitry.neverov
 */
@Test
public class ListFilesTest {

  static {
    Logger.setFactory(new Log4jFactory());
  }

  private TempFiles myTempFiles;
  private GitVcsSupport myGit;
  private VcsRoot myRoot;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    ServerPluginConfig config = new PluginConfigBuilder(serverPaths).build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    myGit = new GitVcsSupport(config, new ResetCacheRegister(), transportFactory, fetchCommand, repositoryManager, null);
    File remoteRepositoryDir = new File(myTempFiles.createTempDir(), "repo.git");
    FileUtil.copyDir(dataFile("repo.git"), remoteRepositoryDir);
    myRoot = vcsRoot().withFetchUrl(remoteRepositoryDir.getAbsolutePath()).withBranch("patch-tests").build();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  public void list_files_in_existing_dir() throws Exception {
    ListDirectChildrenPolicy policy = getListFilesPolicy(myRoot);
    List<VcsFileData> files = policy.listFiles("");
    assertThat(files, hasItems(vcsDir("dir1"),
                               vcsDir("dir with space"),
                               vcsDir("submodule"),
                               vcsFile("file_in_branch.txt"),
                               vcsFile(".gitmodules"),
                               vcsFile("submodule.txt")));
    files = policy.listFiles("dir1");
    assertThat(files, hasItems(vcsDir("subdir"),
                               vcsFile("file1.txt"),
                               vcsFile("file3.txt")));
    files = policy.listFiles("dir with space");
    assertThat(files, hasItems(vcsFile("file with space.txt")));
  }


  @Test(expectedExceptions = VcsFileNotFoundException.class)
  public void list_files_not_existing_dir() throws VcsException {
    ListDirectChildrenPolicy policy = getListFilesPolicy(myRoot);
    policy.listFiles("not/existing/dir");
  }


  public void list_submodule_as_empty_dir() throws VcsException {
    ListDirectChildrenPolicy policy = getListFilesPolicy(myRoot);
    assertTrue(policy.listFiles("submodule").isEmpty());
  }


  public void list_files_in_dir_which_contains_only_dirs() throws Exception {
    ListDirectChildrenPolicy policy = getListFilesPolicy(myRoot);
    List<VcsFileData> files = policy.listFiles("dir/subdir");
    assertThat(files, hasItems(vcsDir("b"), vcsDir("c"), vcsDir("d")));
  }


  @NotNull
  private ListDirectChildrenPolicy getListFilesPolicy(VcsRoot root) {
    ListDirectChildrenPolicy policy = (ListDirectChildrenPolicy) myGit.getListFilesPolicy(root);
    assert policy != null;
    return policy;
  }

  static class VcsFileDataMatcher extends TypeSafeMatcher<VcsFileData> {

    private final String myName;
    private final boolean myDirectory;

    VcsFileDataMatcher(@NotNull String name, boolean directory) {
      myName = name;
      myDirectory = directory;
    }

    @NotNull
    static VcsFileDataMatcher vcsFile(@NotNull String name) {
      return new VcsFileDataMatcher(name, false);
    }

    @NotNull
    static VcsFileDataMatcher vcsDir(@NotNull String name) {
      return new VcsFileDataMatcher(name, true);
    }

    @Override
    public boolean matchesSafely(VcsFileData file) {
      return myName.equals(file.getName()) &&
             myDirectory == file.isDirectory();
    }

    public void describeTo(Description description) {
      description.appendText(myDirectory ? "dir" : "file").appendText(" with name ").appendValue(myName);
    }
  }
}
