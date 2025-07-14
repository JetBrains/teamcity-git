package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.impl.configsRepo.CentralRepositoryConfiguration;
import jetbrains.buildServer.serverSide.impl.configsRepo.RepositoryInitializingExtension;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static org.testng.Assert.*;

@Test
public class GitRepositoryInitializingExtensionTest extends BaseRemoteRepositoryTest {

  private GitRepositoryInitializingExtension myExtension;
  private GitVcsSupport myVcsSupport;
  private ServerPaths myServerPaths;
  private File originRepository;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    originRepository = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    final ServerPluginConfig config = new PluginConfigBuilder(myServerPaths).build();
    final VcsRootSshKeyManager keyManager = r -> null;
    final TransportFactoryImpl transportFactory = new TransportFactoryImpl(config, keyManager, myKnownHostsManager);
    final GitRepoOperationsImpl repoOperations = new GitRepoOperationsImpl(config, transportFactory, keyManager,
                                                                           new FetchCommandImpl(config, transportFactory,
                                                                                                new FetcherProperties(config),
                                                                                                keyManager, myKnownHostsManager), myKnownHostsManager);
    myVcsSupport = gitSupport().withPluginConfig(config).build();
    myExtension = new GitRepositoryInitializingExtension(myVcsSupport, repoOperations, myServerPaths);

    myExtension.createSelfHostedRepository(originRepository.toPath());

    setInternalProperty("teamcity.git.nativeOperationsEnabled", "true");
  }

  public void test_double_initializing() throws IOException, VcsException {
    File configDir = new File(myServerPaths.getConfigDir());
    File file = new File(configDir, "file");
    FileUtil.writeToFile(file, "123".getBytes(StandardCharsets.UTF_8));
    Map<String, String> params = myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Collections.emptyList(), new CommitAllProcessor());

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    ListDirectChildrenPolicy listFilesPolicy = (ListDirectChildrenPolicy)myVcsSupport.getListFilesPolicy();
    assertNotNull(listFilesPolicy);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());

    VcsFileContentProvider contentProvider = myVcsSupport.getContentProvider();

    assertNotNull(listFilesPolicy);
    RepositoryStateData firstState = myVcsSupport.getCurrentState(vcsRoot);
    assertFileContent("123".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, firstState, contentProvider);

    FileUtil.writeToFile(file, "456".getBytes(StandardCharsets.UTF_8));

    myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Collections.emptyList(), new CommitAllProcessor());

    assertFileContent("123".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, firstState, contentProvider);
    assertFileContent("456".getBytes(StandardCharsets.UTF_8), "file", vcsRoot, myVcsSupport.getCurrentState(vcsRoot), contentProvider);
    assertEquals(1, listFilesPolicy.listFiles(vcsRoot, "").size());
  }

  @NotNull
  private CentralRepositoryConfiguration getRepositoryConfiguration() {
    CentralRepositoryConfiguration centralRepositoryConfiguration = new CentralRepositoryConfiguration();
    centralRepositoryConfiguration.setEnabled(true);
    centralRepositoryConfiguration.setSelfHosted(true);
    centralRepositoryConfiguration.setRepositoryUrl("file://" + originRepository.getAbsolutePath());
    centralRepositoryConfiguration.setBranch("refs/heads/master");
    return centralRepositoryConfiguration;
  }

  public void test_gitignore_exists() throws IOException, VcsException {
    File configDir = new File(myServerPaths.getConfigDir());
    File gitignore = new File(configDir, ".gitignore");
    FileUtil.writeToFile(gitignore, "123".getBytes(StandardCharsets.UTF_8));
    Map<String, String> params = myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Arrays.asList("456", "123"), new CommitAllProcessor());

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
    File configDir = new File(myServerPaths.getConfigDir());
    configDir.mkdirs();
    Map<String, String> params = myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Collections.emptyList(), new CommitAllProcessor());

    VcsRootImpl vcsRoot = new VcsRootImpl(-1, myVcsSupport.getName(), params);

    try {
      myVcsSupport.getCurrentState(vcsRoot);
      fail("can't get current state since there is no commits");
    } catch (VcsException ignored) {
    }

    File file = new File(configDir, "file");
    FileUtil.writeToFile(file, "abc".getBytes(StandardCharsets.UTF_8));

    myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Collections.emptyList(), new CommitAllProcessor());

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
    File configDir = new File(myServerPaths.getConfigDir());
    File file1 = new File(configDir, "toCommit");
    File file2 = new File(configDir, "toCommit2");
    File ignoredFile = new File(configDir, "ignoredFile");

    FileUtil.writeToFile(file1, "123".getBytes(StandardCharsets.UTF_8));
    FileUtil.writeToFile(file2, "456".getBytes(StandardCharsets.UTF_8));
    FileUtil.writeToFile(ignoredFile, "456".getBytes(StandardCharsets.UTF_8));

    Map<String, String> params = myExtension.commitAllChanges(getRepositoryConfiguration(), getCommitSettings(), Collections.singletonList("ignoredFile"), new CommitAllProcessor()
    );

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
      fail("file " + ignoredFile + " shouldn't be committed into repository");
    } catch (VcsFileNotFoundException ignored) {
    }
  }

  private static void assertFileContent(byte[] expectedContent,
                                        String filePathRelativeToRepo,
                                        VcsRootImpl vcsRoot,
                                        RepositoryStateData currentState,
                                        VcsFileContentProvider contentProvider) throws VcsException {
    assertEquals(expectedContent, contentProvider.getContent(filePathRelativeToRepo, vcsRoot, Objects.requireNonNull(currentState.getDefaultBranchRevision())));
  }

  static class CommitAllProcessor implements RepositoryInitializingExtension.FilesProcessor {
    @Override
    public RepositoryInitializingExtension.ProcessResult process(Path path) {
      return RepositoryInitializingExtension.ProcessResult.COMMIT;
    }
  }
}
