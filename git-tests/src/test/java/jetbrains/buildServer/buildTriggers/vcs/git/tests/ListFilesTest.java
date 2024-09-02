

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collection;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
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
  private TempFiles myTempFiles;
  private GitVcsSupport myGit;
  private VcsRoot myRoot;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    myGit = gitSupport().withServerPaths(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath())).build();
    File remoteRepositoryDir = new File(myTempFiles.createTempDir(), "repo.git");
    FileUtil.copyDir(dataFile("repo.git"), remoteRepositoryDir);
    myRoot = vcsRoot().withFetchUrl(remoteRepositoryDir.getAbsolutePath()).withBranch("patch-tests").build();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  public void list_files_in_existing_dir() throws Exception {
    ListDirectChildrenPolicy policy = getListFilesPolicy();
    Collection<VcsFileData> files = policy.listFiles(myRoot, "");
    assertThat(files, hasItems(vcsDir("dir1"),
                               vcsDir("dir with space"),
                               vcsDir("submodule"),
                               vcsFile("file_in_branch.txt"),
                               vcsFile(".gitmodules"),
                               vcsFile("submodule.txt")));
    files = policy.listFiles(myRoot, "dir1");
    assertThat(files, hasItems(vcsDir("subdir"),
                               vcsFile("file1.txt"),
                               vcsFile("file3.txt")));
    files = policy.listFiles(myRoot, "dir with space");
    assertThat(files, hasItems(vcsFile("file with space.txt")));
  }


  @Test(expectedExceptions = VcsFileNotFoundException.class)
  public void list_files_not_existing_dir() throws VcsException {
    ListDirectChildrenPolicy policy = getListFilesPolicy();
    policy.listFiles(myRoot, "not/existing/dir");
  }


  public void list_submodule_as_empty_dir() throws VcsException {
    ListDirectChildrenPolicy policy = getListFilesPolicy();
    assertTrue(policy.listFiles(myRoot, "submodule").isEmpty());
  }


  public void list_files_in_dir_which_contains_only_dirs() throws Exception {
    ListDirectChildrenPolicy policy = getListFilesPolicy();
    Collection<VcsFileData> files = policy.listFiles(myRoot, "dir/subdir");
    assertThat(files, hasItems(vcsDir("b"), vcsDir("c"), vcsDir("d")));
  }


  @NotNull
  private ListDirectChildrenPolicy getListFilesPolicy() {
    ListDirectChildrenPolicy policy = (ListDirectChildrenPolicy) myGit.getListFilesPolicy();
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