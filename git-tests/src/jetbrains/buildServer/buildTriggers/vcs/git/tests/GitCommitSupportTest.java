/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.ByteArrayInputStream;
import java.io.File;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
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
                              (a,b,c) -> {});
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
                                                                                                                          (a,b,c) -> {}) {
      @NotNull
      @Override
      public PushCommand pushCommand(@NotNull String repoUrl) {
        return new NativeGitCommands(config, GitCommitSupportTest::detectGitStub, r -> null, null) {
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
