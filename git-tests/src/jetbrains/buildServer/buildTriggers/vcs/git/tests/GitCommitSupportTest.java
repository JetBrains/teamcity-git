/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.GitCommitSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.assertj.core.groups.Tuple;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.*;

@Test
public class GitCommitSupportTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;
  private CommitSupport myCommitSupport;
  private VcsRoot myRoot;

  public GitCommitSupportTest() {
    super("merge");
  }

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myCommitSupport = new GitCommitSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(), builder.getTransportFactory());
    myRoot = vcsRoot().withFetchUrl(getRemoteRepositoryDir("merge")).build();
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
