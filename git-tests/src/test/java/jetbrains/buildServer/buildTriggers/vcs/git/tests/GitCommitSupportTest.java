

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitCommands;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FuncThrow;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.assertj.core.groups.Tuple;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.*;

@Test
public class GitCommitSupportTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;
  private CommitSupport myCommitSupport;
  private VcsRoot myRoot;
  private ServerPaths myPaths;
  private GitRepoOperationsImpl myGitRepoOperations;

  public GitCommitSupportTest() {
    super("merge");
  }

  @NotNull
  private static String showRef(@NotNull File repo, @NotNull String ref) throws Exception {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkDirectory(repo.getAbsolutePath());
    cmd.setExePath("git");
    cmd.addParameters("show-ref", ref);
    final ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    if (result.getExitCode() == 0 && result.getStderr().isEmpty()) {
      return result.getStdout();
    }
    throw new Exception(result.toString());
  }


  public void test_commit() throws Exception {
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    byte[] bytes = "test-content".getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
    patchBuilder.dispose();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);
    assertEquals(1, changes.size());
    ModificationData m = changes.get(0);
    assertEquals("user", m.getUserName());
    assertEquals("Commit description", m.getDescription());
    assertEquals("file-to-commit", m.getChanges().get(0).getFileName());
  }

  public void test_fetch_updates_local_clone() throws Exception {
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    byte[] bytes = "test-content".getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    String createdRevision = patchBuilder.commit(new CommitSettingsImpl("user", "Commit description")).getCreatedRevision();
    patchBuilder.dispose();

    assertFalse(StringUtil.isEmpty(createdRevision));


    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    final File mirror = myGit.getRepositoryManager()
                             .getMirrorDir(getRemoteRepositoryDir("merge").getAbsolutePath());

    String ref = showRef(mirror, "refs/heads/master");

    assertFalse(StringUtil.isEmpty(ref));

    // the following conditional assertions document the current behaviour
    if (myGitRepoOperations.isNativeGitOperationsEnabled())
      assertTrue(ref.startsWith(createdRevision));
    else
      assertFalse(ref.startsWith(createdRevision));

    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);

    ref = showRef(mirror, "refs/heads/master");

    assertTrue(ref.startsWith(createdRevision));
  }


  public void test_short_branch_name() throws Exception {
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    byte[] bytes = "test-content".getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
    patchBuilder.dispose();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);
    assertEquals(1, changes.size());
    ModificationData m = changes.get(0);
    assertEquals("user", m.getUserName());
    assertEquals("Commit description", m.getDescription());
    assertEquals("file-to-commit", m.getChanges().get(0).getFileName());
  }

  @TestFor(issues = "TW-38226")
  public void should_canonicalize_line_endings_on_commit() throws Exception {
    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    String committedContent = "a\r\nb\r\nc\r\n";
    byte[] bytes = committedContent.getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
    patchBuilder.dispose();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    byte[] content = myGit.getContentProvider().getContent("file-to-commit", myRoot, state2.getBranchRevisions().get(state2.getDefaultBranchName()));
    assertEquals("Line-endings were not normalized", "a\nb\nc\n", new String(content));

    VcsRoot autoCrlfRoot = vcsRoot().withAutoCrlf(true).withFetchUrl(getRemoteRepositoryDir("merge")).build();
    assertEquals(committedContent, new String(myGit.getContentProvider().getContent("file-to-commit", autoCrlfRoot, state2.getBranchRevisions().get(state2.getDefaultBranchName()))));
  }


  @TestFor(issues = "TW-39051")
  public void should_throw_meaningful_error_if_destination_branch_doesnt_exist() throws Exception {
    String nonExistingBranch = "refs/heads/nonExisting";
    try {
      VcsRoot root = vcsRoot().withFetchUrl(getRemoteRepositoryDir("merge")).withBranch(nonExistingBranch).build();
      CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(root);
      byte[] bytes = "test-content".getBytes();
      patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
      patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
      patchBuilder.dispose();
      fail();
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("The '" + nonExistingBranch + "' destination branch doesn't exist"));
    }
  }


  @TestFor(issues = "TW-39051")
  public void should_create_branch_if_repository_has_no_branches() throws Exception {
    String nonExistingBranch = "refs/heads/nonExisting";

    File remoteRepo = myTempFiles.createTempDir();
    Repository r = new RepositoryBuilder().setBare().setGitDir(remoteRepo).build();
    r.create(true);
    VcsRoot root = vcsRoot().withFetchUrl(remoteRepo).withBranch(nonExistingBranch).build();

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(root);
    byte[] bytes = "test-content".getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
    patchBuilder.dispose();

    RepositoryStateData state2 = myGit.getCurrentState(root);
    assertNotNull(state2.getBranchRevisions().get(nonExistingBranch));
  }


  @TestFor(issues = "TW-42737")
  public void test_directory_remove() throws Exception {
    //create the dir directory with the a file
    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    patchBuilder.createFile("dir/file", new ByteArrayInputStream("content".getBytes()));
    patchBuilder.createFile("dir2/file", new ByteArrayInputStream("content".getBytes()));
    patchBuilder.commit(new CommitSettingsImpl("user", "Create dir with file"));
    patchBuilder.dispose();

    RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    patchBuilder.deleteDirectory("dir");
    patchBuilder.commit(new CommitSettingsImpl("user", "Delete dir"));
    patchBuilder.dispose();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);

    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);
    then(changes).hasSize(1);
    then(changes.get(0).getChanges()).extracting("fileName", "type").containsOnly(Tuple.tuple("dir/file", VcsChange.Type.REMOVED));
  }


  @TestFor(issues = "TW-48463")
  public void concurrent_commit() throws Exception {
    //make clone on the server, so that none of the merges perform the clone
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/master", map(
      "refs/heads/master", "f727882267df4f8fe0bc58c18559591918aefc54"));
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/master", map(
      "refs/heads/master", "f727882267df4f8fe0bc58c18559591918aefc54",
      "refs/heads/topic2", "cc69c22bd5d25779e58ad91008e685cbbe7f700a"));
    myGit.getCollectChangesPolicy().collectChanges(myRoot, s1, s2, CheckoutRules.DEFAULT);

    RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch t1Ready = new CountDownLatch(1);
    CountDownLatch t2Ready = new CountDownLatch(1);
    AtomicReference<VcsException> error1 = new AtomicReference<>();
    AtomicReference<VcsException> error2 = new AtomicReference<>();
    Thread t1 = new Thread(() -> {
      CommitPatchBuilder patchBuilder = null;
      try {
        patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
        patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("content1".getBytes()));
        t1Ready.countDown();
        latch.await();
        patchBuilder.commit(new CommitSettingsImpl("user", "Commit1"));
      } catch (VcsException e) {
        error1.set(e);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (patchBuilder != null)
          patchBuilder.dispose();
      }
    });
    t1.start();
    Thread t2 = new Thread(() -> {
      CommitPatchBuilder patchBuilder = null;
      try {
        patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
        patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("content2".getBytes()));
        t2Ready.countDown();
        latch.await();
        patchBuilder.commit(new CommitSettingsImpl("user", "Commit2"));
      } catch (VcsException e) {
        error2.set(e);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (patchBuilder != null)
          patchBuilder.dispose();
      }
    });
    t2.start();
    t1Ready.await();
    t2Ready.await();
    latch.countDown();
    t1.join();
    t2.join();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);

    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);

    then(changes.size() == 2 || (error1.get() != null || error2.get() != null)) //either both commits succeeds, or one finishes with an error
      .overridingErrorMessage("Non-fast-forward push succeeds")
      .isTrue();
  }

  // --- Tests for the (VcsRoot, targetBranch) overload introduced by TW-100328 ---

  @TestFor(issues = "TW-100328")
  public void commit_to_existing_target_branch_lands_on_that_branch() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    final String topicBefore = resolveRef(remote, "refs/heads/topic");
    final String masterBefore = resolveRef(remote, "refs/heads/master");
    assertNotNull(topicBefore);
    assertNotNull(masterBefore);

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot, "refs/heads/topic");
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    String created = patchBuilder.commit(new CommitSettingsImpl("user", "Commit to topic")).getCreatedRevision();
    patchBuilder.dispose();

    assertEquals(created, resolveRef(remote, "refs/heads/topic"));
    assertEquals("master must not have been touched", masterBefore, resolveRef(remote, "refs/heads/master"));
    assertEquals("new commit must be parented on the previous topic tip",
                 Collections.singletonList(topicBefore), parentRevisions(remote, created));
  }

  @TestFor(issues = "TW-100328")
  public void commit_to_existing_target_branch_accepts_short_name() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    final String topicBefore = resolveRef(remote, "refs/heads/topic");

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot, "topic");
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    String created = patchBuilder.commit(new CommitSettingsImpl("user", "Commit via short name")).getCreatedRevision();
    patchBuilder.dispose();

    assertEquals(created, resolveRef(remote, "refs/heads/topic"));
    assertEquals(Collections.singletonList(topicBefore), parentRevisions(remote, created));
  }

  @TestFor(issues = "TW-100328")
  public void creates_target_branch_on_demand_parented_on_root_ref() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    final String newBranch = "refs/heads/feature/new-one";
    final String masterBefore = resolveRef(remote, "refs/heads/master");
    assertNotNull(masterBefore);
    assertNull("precondition: target branch must not exist on remote", resolveRef(remote, newBranch));

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot, newBranch);
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    String created = patchBuilder.commit(new CommitSettingsImpl("user", "Create new branch")).getCreatedRevision();
    patchBuilder.dispose();

    assertEquals(created, resolveRef(remote, newBranch));
    assertEquals("master tip must not move when auto-creating a branch from it",
                 masterBefore, resolveRef(remote, "refs/heads/master"));
    assertEquals("new branch's first commit must be parented on master's live tip",
                 Collections.singletonList(masterBefore), parentRevisions(remote, created));
  }

  @TestFor(issues = "TW-100328")
  public void target_branch_auto_create_disabled_throws_legacy_error() throws Exception {
    setInternalProperty("teamcity.git.commit.createTargetBranchOnDemand", "false");
    final File remote = getRemoteRepositoryDir("merge");
    final String newBranch = "refs/heads/nonExisting";
    assertNull(resolveRef(remote, newBranch));

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot, newBranch);
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    try {
      patchBuilder.commit(new CommitSettingsImpl("user", "Should fail"));
      fail("Expected VcsException when target branch is missing and auto-create is disabled");
    } catch (VcsException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("The '" + newBranch + "' destination branch doesn't exist"));
    } finally {
      patchBuilder.dispose();
    }
    assertNull("no ref must have been created when the commit was rejected", resolveRef(remote, newBranch));
  }

  @TestFor(issues = "TW-100328")
  public void target_branch_missing_with_root_ref_also_missing_throws() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    VcsRoot rootOnMissing = vcsRoot().withFetchUrl(remote).withBranch("refs/heads/doesNotExist").build();
    final String newBranch = "refs/heads/alsoMissing";

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(rootOnMissing, newBranch);
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    try {
      patchBuilder.commit(new CommitSettingsImpl("user", "Should fail"));
      fail("Expected VcsException when both target and root's configured ref are missing");
    } catch (VcsException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("The '" + newBranch + "' destination branch doesn't exist"));
    } finally {
      patchBuilder.dispose();
    }
    assertNull(resolveRef(remote, newBranch));
  }

  @TestFor(issues = "TW-100328")
  public void target_branch_override_on_empty_remote_creates_branch() throws Exception {
    File emptyRemote = myTempFiles.createTempDir();
    Repository r = new RepositoryBuilder().setBare().setGitDir(emptyRemote).build();
    r.create(true);
    r.close();
    VcsRoot root = vcsRoot().withFetchUrl(emptyRemote).withBranch("refs/heads/master").build();
    String target = "refs/heads/anyBranch";

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(root, target);
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    String created = patchBuilder.commit(new CommitSettingsImpl("user", "initial")).getCreatedRevision();
    patchBuilder.dispose();

    assertEquals(created, resolveRef(emptyRemote, target));
    assertEquals("initial commit on a fresh ref must have no parents",
                 Collections.emptyList(), parentRevisions(emptyRemote, created));
  }

  @TestFor(issues = "TW-100328")
  public void target_branch_equal_to_root_ref_acts_like_single_arg_api() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    final String masterBefore = resolveRef(remote, "refs/heads/master");
    assertNotNull(masterBefore);

    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot, "refs/heads/master");
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream("test-content".getBytes()));
    String created = patchBuilder.commit(new CommitSettingsImpl("user", "Commit description")).getCreatedRevision();
    patchBuilder.dispose();

    assertEquals("master tip must advance to the new commit", created, resolveRef(remote, "refs/heads/master"));
    assertEquals("new commit must be a fast-forward of master's previous tip",
                 Collections.singletonList(masterBefore), parentRevisions(remote, created));
  }

  @TestFor(issues = "TW-100328")
  public void concurrent_commits_to_different_target_branches_succeed() throws Exception {
    final File remote = getRemoteRepositoryDir("merge");
    // ensure the server-side clone exists upfront so neither thread races on the initial clone
    myGit.getCurrentState(myRoot);
    final String masterBefore = resolveRef(remote, "refs/heads/master");

    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch t1Ready = new CountDownLatch(1);
    CountDownLatch t2Ready = new CountDownLatch(1);
    AtomicReference<Exception> error1 = new AtomicReference<>();
    AtomicReference<Exception> error2 = new AtomicReference<>();
    AtomicReference<String> rev1 = new AtomicReference<>();
    AtomicReference<String> rev2 = new AtomicReference<>();

    Thread t1 = new Thread(() -> {
      CommitPatchBuilder b = null;
      try {
        b = myCommitSupport.getCommitPatchBuilder(myRoot, "refs/heads/branch-A");
        b.createFile("file-A", new ByteArrayInputStream("a".getBytes()));
        t1Ready.countDown();
        latch.await();
        rev1.set(b.commit(new CommitSettingsImpl("user", "A")).getCreatedRevision());
      } catch (Exception e) {
        error1.set(e);
      } finally {
        if (b != null) b.dispose();
      }
    });
    Thread t2 = new Thread(() -> {
      CommitPatchBuilder b = null;
      try {
        b = myCommitSupport.getCommitPatchBuilder(myRoot, "refs/heads/branch-B");
        b.createFile("file-B", new ByteArrayInputStream("b".getBytes()));
        t2Ready.countDown();
        latch.await();
        rev2.set(b.commit(new CommitSettingsImpl("user", "B")).getCreatedRevision());
      } catch (Exception e) {
        error2.set(e);
      } finally {
        if (b != null) b.dispose();
      }
    });
    t1.start();
    t2.start();
    t1Ready.await();
    t2Ready.await();
    latch.countDown();
    t1.join();
    t2.join();

    assertNull("thread A must succeed: " + error1.get(), error1.get());
    assertNull("thread B must succeed: " + error2.get(), error2.get());
    assertEquals(rev1.get(), resolveRef(remote, "refs/heads/branch-A"));
    assertEquals(rev2.get(), resolveRef(remote, "refs/heads/branch-B"));
    assertEquals("master must not be touched by either branch creation",
                 masterBefore, resolveRef(remote, "refs/heads/master"));
  }

  @Nullable
  private static String resolveRef(@NotNull File bareRepo, @NotNull String ref) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build()) {
      Ref reference = r.exactRef(GitUtils.expandRef(ref));
      return reference == null ? null : reference.getObjectId().name();
    }
  }

  @NotNull
  private static List<String> parentRevisions(@NotNull File bareRepo, @NotNull String revision) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      RevCommit commit = rw.parseCommit(ObjectId.fromString(revision));
      List<String> parents = new ArrayList<>(commit.getParentCount());
      for (RevCommit p : commit.getParents()) {
        parents.add(p.getId().name());
      }
      return parents;
    }
  }

  @NotNull
  private static GitExec detectGitStub() {
    return new GitExec("git", GitVersion.MIN);
  }

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myGitRepoOperations = new GitRepoOperationsImpl(builder.getPluginConfig(),
                              builder.getTransportFactory(),
                              r -> null,
                              (a,b,c) -> {},
                              myKnownHostsManager);
    myCommitSupport = new GitCommitSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(), myGitRepoOperations);
    myRoot = vcsRoot().withFetchUrl(getRemoteRepositoryDir("merge")).build();
  }

  @Test
  public void local_state_restored_if_push_fails() throws Exception {
    // perform successfull commit to make local repo not empty
    CommitPatchBuilder patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    byte[] bytes = "test-content".getBytes();
    patchBuilder.createFile("file-to-commit", new ByteArrayInputStream(bytes));
    patchBuilder.commit(new CommitSettingsImpl("user", "Commit description"));
    patchBuilder.dispose();

    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    builder.build();
    final ServerPluginConfig config = builder.getPluginConfig();
    final RepositoryManager repositoryManager = builder.getRepositoryManager();
    myCommitSupport = new GitCommitSupport(myGit, builder.getCommitLoader(), repositoryManager, new GitRepoOperationsImpl(config,
                                                                                                                          builder.getTransportFactory(),
                                                                                                                          r -> null,
                                                                                                                          (a,b,c) -> {}, myKnownHostsManager) {
      @NotNull
      @Override
      public PushCommand pushCommand(@NotNull String repoUrl) {
        return new NativeGitCommands(config, GitCommitSupportTest::detectGitStub, r -> null, null, myKnownHostsManager) {
          @Override
          protected <R> R executeCommand(@NotNull Context ctx, @NotNull String action, @NotNull String debugInfo, @NotNull FuncThrow<R, VcsException> cmd, @NotNull GitFacade gitFacade) throws VcsException {
            throw new VcsException("Always fails");
          }
        };
      }
    });

    final File mirror = repositoryManager.getMirrorDir(getRemoteRepositoryDir("merge").getAbsolutePath());
    final String before = showRef(mirror, "refs/heads/master");
    final RepositoryStateData state1 = myGit.getCurrentState(myRoot);

    patchBuilder = myCommitSupport.getCommitPatchBuilder(myRoot);
    bytes = "new-test-content".getBytes();
    patchBuilder.createFile("new-file-to-commit", new ByteArrayInputStream(bytes));
    try {
      patchBuilder.commit(new CommitSettingsImpl("user", "New commit description"));
    } catch (VcsException e) {
      //expected
    }
    patchBuilder.dispose();

    assertEquals(state1, myGit.getCurrentState(myRoot));
    assertEquals(before, showRef(mirror, "refs/heads/master"));
  }

  private class CommitSettingsImpl implements CommitSettings {
    private final String myUserName;
    private final String myDescription;

    public CommitSettingsImpl(@NotNull String userName, @NotNull String description) {
      myUserName = userName;
      myDescription = description;
    }

    @NotNull
    public String getUserName() {
      return myUserName;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }
}