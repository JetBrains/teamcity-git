package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static junit.framework.Assert.*;

@Test
public class GitRepositoryInitializingExtensionTest extends BaseRemoteRepositoryTest {

  private GitRepositoryInitializingExtension myExtension;
  private GitVcsSupport myVcsSupport;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();


    final ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    final ServerPluginConfig config = new PluginConfigBuilder(serverPaths).build();
    final VcsRootSshKeyManager keyManager = r -> null;
    final TransportFactoryImpl transportFactory = new TransportFactoryImpl(config, keyManager);
    final GitRepoOperationsImpl repoOperations = new GitRepoOperationsImpl(config, transportFactory, keyManager,
                                                                           new FetchCommandImpl(config, transportFactory,
                                                                                                new FetcherProperties(config),
                                                                                                keyManager));
    myVcsSupport = gitSupport().withPluginConfig(config).build();
    myExtension = new GitRepositoryInitializingExtension(myVcsSupport, repoOperations);

    setInternalProperty("teamcity.git.nativeOperationsEnabled", "true");
  }

  public void test_double_initializing() throws IOException, VcsException {
    File repository = myTempFiles.createTempDir();
    File file = new File(repository, "file");
    FileUtil.writeFile(file, "123", "UTF-8");
    Map<String, String> params = myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Collections.emptyList());

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    ListDirectChildrenPolicy listFilesPolicy = (ListDirectChildrenPolicy)myVcsSupport.getListFilesPolicy();
    assertNotNull(listFilesPolicy);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());

    VcsFileContentProvider contentProvider = myVcsSupport.getContentProvider();

    assertNotNull(listFilesPolicy);
    RepositoryStateData firstState = myVcsSupport.getCurrentState(vcsRoot);
    assertFileContent("123".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, firstState, contentProvider);

    FileUtil.writeFile(file, "456", "UTF-8");

    myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Collections.emptyList());

    assertFileContent("123".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, firstState, contentProvider);
    assertFileContent("456".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, myVcsSupport.getCurrentState(vcsRoot), contentProvider);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());
  }

  public void test_gitignore_exists() throws IOException, VcsException {
    File repository = myTempFiles.createTempDir();
    File gitignore = new File(repository, ".gitignore");
    FileUtil.writeFile(gitignore, "123", "UTF-8");
    Map<String, String> params = myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Arrays.asList("456", "123"));

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    ListDirectChildrenPolicy listFilesPolicy = (ListDirectChildrenPolicy)myVcsSupport.getListFilesPolicy();
    assertNotNull(listFilesPolicy);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());

    VcsFileContentProvider contentProvider = myVcsSupport.getContentProvider();

    assertNotNull(listFilesPolicy);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8.newEncoder()));
    writer.append("123");
    writer.newLine();
    writer.append("456");
    writer.newLine();
    writer.close();

    assertFileContent(baos.toByteArray(), ".gitignore", vcsRoot, myVcsSupport.getCurrentState(vcsRoot), contentProvider);

  }

  public void test_skip_commit_if_no_changes() throws VcsException, IOException {
    File repository = myTempFiles.createTempDir();
    Map<String, String> params = myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Collections.emptyList());

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    try {
      myVcsSupport.getCurrentState(vcsRoot);
      fail("can't get current state since there is no commits");
    } catch (VcsException ignored) {
    }

    File file = new File(repository, "file");
    FileUtil.writeFile(file, "abc", "UTF-8");

    myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Collections.emptyList());

    ListDirectChildrenPolicy listFilesPolicy = (ListDirectChildrenPolicy)myVcsSupport.getListFilesPolicy();
    assertNotNull(listFilesPolicy);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());

    assertFileContent("abc".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, myVcsSupport.getCurrentState(vcsRoot), myVcsSupport.getContentProvider());
  }

  @NotNull
  private static CommitSettings getCommitSettings() {
    return new CommitSettings() {
      @NotNull
      @Override
      public String getUserName() {
        return "test";
      }

      @NotNull
      @Override
      public String getDescription() {
        return "test description";
      }
    };
  }

  public void test_ignored_path() throws Exception {
    File repository = myTempFiles.createTempDir();
    File file1 = new File(repository, "toCommit");
    File file2 = new File(repository, "toCommit2");
    File ignoredFile = new File(repository, "ignoredFile");

    FileUtil.writeFile(file1, "123", "UTF-8");
    FileUtil.writeFile(file2, "456", "UTF-8");
    FileUtil.writeFile(ignoredFile, "456", "UTF-8");

    Map<String, String> params = myExtension.initialize(repository.getAbsolutePath(), getCommitSettings(), Collections.singletonList("ignoredFile"));

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    ListDirectChildrenPolicy listFilesPolicy = (ListDirectChildrenPolicy)myVcsSupport.getListFilesPolicy();
    RepositoryStateData currentState = myVcsSupport.getCurrentState(vcsRoot);
    VcsFileContentProvider contentProvider = myVcsSupport.getContentProvider();

    assertNotNull(listFilesPolicy);

    assertFileContent("123".getBytes(StandardCharsets.UTF_8), "toCommit", vcsRoot, currentState, contentProvider);
    assertFileContent("456".getBytes(StandardCharsets.UTF_8), "toCommit2", vcsRoot, currentState, contentProvider);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8.newEncoder()));
    writer.append("ignoredFile");
    writer.newLine();
    writer.close();

    assertFileContent(baos.toByteArray(), ".gitignore", vcsRoot, currentState, contentProvider);
    assertEquals(3, listFilesPolicy.listFiles(vcsRoot, "").size());//file1, file2, .gitignore

    try {
      contentProvider.getContent(ignoredFile.getName(), vcsRoot, Objects.requireNonNull(currentState.getDefaultBranchRevision()));
      Assert.fail("file " + ignoredFile + " shouldn't be committed into repository");
    } catch (VcsFileNotFoundException ignored) {
    }
  }

  private static void assertFileContent(byte[] expectedContent,
                                        String filePathRelativeToRepo,
                                        VcsRootImpl vcsRoot,
                                        RepositoryStateData currentState,
                                        VcsFileContentProvider contentProvider) throws VcsException {
    Assert.assertEquals(expectedContent, contentProvider.getContent(filePathRelativeToRepo, vcsRoot, Objects.requireNonNull(currentState.getDefaultBranchRevision())));
  }
}
