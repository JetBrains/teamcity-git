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

import jetbrains.buildServer.buildTriggers.vcs.git.GitMergeSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class GitMergeSupportTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;
  private MergeSupport myMergeSupport;
  private VcsRoot myRoot;

  public GitMergeSupportTest() {
    super("merge");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myMergeSupport = new GitMergeSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(), builder.getTransportFactory());
    myRoot = vcsRoot().withFetchUrl(getRemoteRepositoryDir("merge")).build();
  }

  @AfterMethod
  public void tearDown() {
    super.tearDown();
  }


  public void should_fetch_missing_source_commit_for_merge() throws Exception {
    //cc69c22bd5d25779e58ad91008e685cbbe7f700a is not reachable from refs/heads/master
    MergeResult result = myMergeSupport.merge(myRoot, "cc69c22bd5d25779e58ad91008e685cbbe7f700a", "refs/heads/master", "merge", new MergeOptions());
    assertTrue(result.isSuccess());
  }


  public void should_not_create_merge_commit_when_destination_branch_tip_is_reachable_from_merged_commit() throws Exception {
    RepositoryStateData stateBeforeMerge = myGit.getCurrentState(myRoot);
    //tip of the master is reachable from cc69c22bd5d25779e58ad91008e685cbbe7f700a
    myMergeSupport.merge(myRoot, "cc69c22bd5d25779e58ad91008e685cbbe7f700a", "refs/heads/master", "merge", new MergeOptions());
    RepositoryStateData stateAfterMerge = myGit.getCurrentState(myRoot);

    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, stateBeforeMerge, stateAfterMerge, CheckoutRules.DEFAULT);
    assertTrue(changes.isEmpty());
  }


  public void test_rebase() throws Exception {
    RepositoryStateData stateBeforeMerge = myGit.getCurrentState(myRoot);
    MergeResult result = myMergeSupport.merge(myRoot, "d2e06a930fb98746f2208791e6cd5bb41e57ed3f", "refs/heads/master", "merge",
                                            new MergeOptions(map("git.merge.rebase", "true")));
    assertTrue(result.isSuccess());
    RepositoryStateData stateAfterMerge = myGit.getCurrentState(myRoot);

    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, stateBeforeMerge, stateAfterMerge, CheckoutRules.DEFAULT);
    assertEquals(3, changes.size()); //3 changes from branch are rebased
    for (ModificationData change : changes) {
      assertEquals(1, change.getParentRevisions().size()); //no merge commits, changes from branch are rebased
    }
  }
}
