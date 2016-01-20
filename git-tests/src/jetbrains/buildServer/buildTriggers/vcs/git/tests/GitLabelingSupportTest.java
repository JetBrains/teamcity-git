/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Test
public class GitLabelingSupportTest extends BaseRemoteRepositoryTest {

  private PluginConfigBuilder myConfig;

  public GitLabelingSupportTest() {
    super("repo.git", "repo_for_fetch.1", "repo_for_fetch.2");
  }

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myConfig = pluginConfig().setPaths(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
  }


  public void testLabels() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(getRemoteRepositoryDir("repo.git")).build();
    // ensure that all revisions reachable from master are fetched
    buildGit().getLabelingSupport().label("test_label", "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", root, CheckoutRules.DEFAULT);
    Repository r = new RepositoryBuilder().setGitDir(getRemoteRepositoryDir("repo.git")).build();
    RevWalk revWalk = new RevWalk(r);
    try {
      Ref tagRef = r.getTags().get("test_label");
      RevTag t = revWalk.parseTag(tagRef.getObjectId());
      assertEquals(t.getObject().name(), "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653");
    } finally {
      r.close();
    }
  }


  @Test
  public void tag_with_specified_username() throws Exception {
    VcsRoot root = vcsRoot()
      .withFetchUrl(GitUtils.toURL(getRemoteRepositoryDir("repo.git")))
      .withUsernameForTags("John Doe <john.doe@some.org>")
      .build();
    buildGit().getLabelingSupport().label("label_with_specified_username", "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", root, CheckoutRules.DEFAULT);

    Repository r = new RepositoryBuilder().setGitDir(getRemoteRepositoryDir("repo.git")).build();
    RevWalk revWalk = new RevWalk(r);
    try {
      Ref tagRef = r.getTags().get("label_with_specified_username");
      RevTag t = revWalk.parseTag(tagRef.getObjectId());
      PersonIdent tagger = t.getTaggerIdent();
      assertEquals(tagger.getName(), "John Doe");
      assertEquals(tagger.getEmailAddress(), "john.doe@some.org");
    } finally {
      revWalk.release();
      r.close();
    }
  }


  @Test(dataProvider = "true,false")
  public void should_push_all_objects_missing_in_remote_repository(boolean usePackHeuristics) throws Exception {
    myConfig.setUsePackHeuristic(usePackHeuristics);

    GitVcsSupport git = buildGit();
    File remoteRepoDir = getRemoteRepositoryDir("repo_for_fetch.2");
    VcsRoot root = vcsRoot().withFetchUrl(remoteRepoDir).build();

    makeCloneOnServer(git, root);

    //erase commit in the remote repository
    FileUtil.delete(remoteRepoDir);
    remoteRepoDir.mkdirs();
    FileUtil.copyDir(getRemoteRepositoryDir("repo_for_fetch.1"), remoteRepoDir);

    //label erased commit
    String erasedCommit = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
    git.getLabelingSupport().label("label", erasedCommit, root, CheckoutRules.DEFAULT);

    //erased commit should appear in the remote repository
    Repository r = new RepositoryBuilder().setGitDir(remoteRepoDir).build();
    RevWalk walk = new RevWalk(r);
    try {
      walk.parseCommit(ObjectId.fromString(erasedCommit));
    } catch (MissingObjectException e) {
      fail("Not all objects were pushed, labeled commit " + erasedCommit + " is missing");
    } finally {
      walk.release();
      r.close();
    }
  }


  public void fail_labeling_when_heuristics_fails() throws Exception {
    myConfig.setUsePackHeuristic(true);
    myConfig.setFailLabelingWhenPackHeuristicsFail(true);

    GitVcsSupport git = buildGit();
    File remoteRepoDir = getRemoteRepositoryDir("repo_for_fetch.2");
    VcsRoot root = vcsRoot().withFetchUrl(remoteRepoDir).build();

    makeCloneOnServer(git, root);

    //erase commit in the remote repository
    FileUtil.delete(remoteRepoDir);
    remoteRepoDir.mkdirs();
    FileUtil.copyDir(getRemoteRepositoryDir("repo_for_fetch.1"), remoteRepoDir);

    try {
      //label erased commit
      String erasedCommit = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
      git.getLabelingSupport().label("label", erasedCommit, root, CheckoutRules.DEFAULT);
      fail("Should fail labeling since heuristics fails");
    } catch (VcsException e) {
      assertTrue(true);
    }
  }


  private void makeCloneOnServer(@NotNull GitVcsSupport git, @NotNull VcsRoot root) throws VcsException {
    RepositoryStateData currentState = git.getCurrentState(root);
    String unknownRevision = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    String defaultBranchName = currentState.getDefaultBranchName();
    git.collectChanges(root, unknownRevision, currentState.getBranchRevisions().get(defaultBranchName), CheckoutRules.DEFAULT);
  }


  private GitVcsSupport buildGit() {
    return gitSupport().withPluginConfig(myConfig).build();
  }
}
