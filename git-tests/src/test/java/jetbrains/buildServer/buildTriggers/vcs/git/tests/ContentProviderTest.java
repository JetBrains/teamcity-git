

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.*;

@Test
public class ContentProviderTest extends BaseRemoteRepositoryTest {

  private PluginConfigBuilder myConfigBuilder;

  public ContentProviderTest() {
    super("repo.git", "submodule.git");
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myConfigBuilder = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void testGetContent(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("version-test")
      .build();
    String version = getDefaultBranchRevision(git, root);
    byte[] actual = git.getContentProvider().getContent("readme.txt", root, version);
    byte[] expected = FileUtil.loadFileBytes(dataFile("content", "readme.txt"));
    assertEquals(expected, actual);
  }


  @Test(expectedExceptions = VcsFileNotFoundException.class)
  public void should_throw_exception_for_unknown_path() throws Exception {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("version-test")
      .build();
    String version = getDefaultBranchRevision(git, root);
    git.getContentProvider().getContent("unknown file path", root, version);
  }


  @Test(dataProvider = "doFetchInSeparateProcess", dataProviderClass = FetchOptionsDataProvider.class)
  public void get_content_in_submodules(boolean fetchInSeparateProcess) throws Exception {
    myConfigBuilder.setSeparateProcessForFetch(fetchInSeparateProcess);
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("patch-tests")
      .withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT)
      .build();
    String version = getDefaultBranchRevision(git, root);
    byte[] actual = git.getContentProvider().getContent("submodule/file.txt", root, version);
    byte[] expected = FileUtil.loadFileBytes(dataFile("content", "submodule file.txt"));
    assertEquals(expected, actual);
  }


  public void should_use_modification_revision_when_revision_after_is_null() throws Exception {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("version-test")
      .build();
    String version = getDefaultBranchRevision(git, root);
    VcsModification m = new MockVcsModification(version);
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", null, null);
    byte[] actual = git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.AFTER_CHANGE, root);
    byte[] expected = FileUtil.loadFileBytes(dataFile("content", "readme.txt"));
    assertEquals(expected, actual);
  }


  public void should_use_modification_revision_when_revision_after_is_invalid_sha() throws Exception {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
    VcsModification m = new MockVcsModification("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f");
    String invalidSHA = "invalidSHA";
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", null, invalidSHA);
    byte[] actual = git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.AFTER_CHANGE, root);
    byte[] expected = "Test repository for teamcity.change 1\n".getBytes();
    assertEquals(expected, actual);
  }


  public void should_use_modification_revision_when_revision_before_is_null() throws Exception {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
    VcsModification m = new MockVcsModification("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f");
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", null, null);
    byte[] actual = git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.BEFORE_CHANGE, root);
    byte[] expected = "Test repository for teamcity.".getBytes();
    assertEquals(expected, actual);
  }


  public void should_use_modification_revision_when_revision_before_is_invalid_sha() throws Exception {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
    VcsModification m = new MockVcsModification("3b9fbfbb43e7edfad018b482e15e7f93cca4e69f");
    String invalidSHA = "invalidSHA";
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", invalidSHA, null);
    byte[] actual = git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.BEFORE_CHANGE, root);
    byte[] expected = "Test repository for teamcity.".getBytes();
    assertEquals(expected, actual);
  }


  public void should_throw_error_when_cannot_get_valid_version() throws VcsException {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("version-test")
      .build();
    VcsModification m = new MockVcsModification("invalidSHA");
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", "invalidSHA", "invalidSHA");
    try {
      git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.BEFORE_CHANGE, root);
      fail("should fail");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Invalid version 'invalidSHA'"));
    }
  }

  public void should_throw_specific_exception_when_revision_does_not_exist() throws VcsException {
    GitVcsSupport git = createGit();
    VcsRoot root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
    VcsModification m = new MockVcsModification("9b9fbfbb43e7edfad018b482e15e7f93cca4e69f"); // some non existing revision
    VcsChangeInfo change = new VcsChange(VcsChangeInfo.Type.CHANGED, "readme.txt", "readme.txt", m.getVersion(), m.getVersion());
    try {
      git.getContentProvider().getContent(m, change, VcsChangeInfo.ContentType.AFTER_CHANGE, root);
      fail("should fail");
    } catch (RevisionNotFoundException e) {
      assertTrue(e.getMessage().contains("Cannot find commit " + m.getVersion()));
    }
  }


  private String getDefaultBranchRevision(@NotNull GitVcsSupport git, @NotNull VcsRoot root) throws VcsException {
    RepositoryStateData state = git.getCurrentState(root);
    return state.getBranchRevisions().get(state.getDefaultBranchName());
  }


  @NotNull
  private GitVcsSupport createGit() {
    return gitSupport().withPluginConfig(myConfigBuilder).build();
  }
}