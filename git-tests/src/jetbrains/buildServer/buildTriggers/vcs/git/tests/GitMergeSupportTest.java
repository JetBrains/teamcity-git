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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.buildTriggers.vcs.git.GitMergeSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class GitMergeSupportTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;
  private MergeSupport myMergeSupport;
  private VcsRoot myRoot;
  private ServerPaths myPaths;
  private GitRepoOperationsImpl myRepoOperations;

  public GitMergeSupportTest() {
    super("merge");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myRepoOperations = new GitRepoOperationsImpl(builder.getPluginConfig(),
                                                 builder.getTransportFactory(),
                                                 r -> null,
                                                 (a, b, c) -> {});
    myMergeSupport = new GitMergeSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(),
                                         builder.getPluginConfig(),
                                         myRepoOperations);
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
    myMergeSupport.merge(myRoot, "cc69c22bd5d25779e58ad91008e685cbbe7f700a", "refs/heads/master", "merge", new MergeOptions(map("teamcity.merge.policy", "fastForward")));
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


  @TestFor(issues = "TW-36826")
  public void multiple_merge_bases_support() throws Exception {
    RepositoryStateData stateBeforeMerge = myGit.getCurrentState(myRoot);
    MergeResult result = myMergeSupport.merge(myRoot, "080f42bbf244b09d98569644cdf8609777f23d15", "refs/heads/topic3", "merge", new MergeOptions());
    assertTrue(result.isSuccess());
    RepositoryStateData stateAfterMerge = myGit.getCurrentState(myRoot);

    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(myRoot, stateBeforeMerge, stateAfterMerge, CheckoutRules.DEFAULT);
    ModificationData m = changes.get(0);
    assertTrue(m.getParentRevisions().containsAll(asList(stateBeforeMerge.getBranchRevisions().get("refs/heads/topic"),
                                                         stateBeforeMerge.getBranchRevisions().get("refs/heads/topic3"))));
  }


  @TestFor(issues = "TW-48463")
  public void concurrent_merge() throws Exception {
    GitSupportBuilder builder = gitSupport().withPluginConfig(pluginConfig().setPaths(myPaths).setMergeRetryAttempts(0));//disable merge retries
    myGit = builder.build();
    myMergeSupport = new GitMergeSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(), builder.getPluginConfig(), myRepoOperations);

    //make clone on the server, so that none of the merges perform the clone
    RepositoryStateData s1 = RepositoryStateData.createVersionState("refs/heads/master", map(
      "refs/heads/master", "f727882267df4f8fe0bc58c18559591918aefc54"));
    RepositoryStateData s2 = RepositoryStateData.createVersionState("refs/heads/master", map(
      "refs/heads/master", "f727882267df4f8fe0bc58c18559591918aefc54",
      "refs/heads/topic2", "cc69c22bd5d25779e58ad91008e685cbbe7f700a",
      "refs/heads/topic3", "68b73163526a29a1f5a341f3b6fcd0d928748579"));
    myGit.getCollectChangesPolicy().collectChanges(myRoot, s1, s2, CheckoutRules.DEFAULT);

    RepositoryStateData state1 = myGit.getCurrentState(myRoot);
    //run concurrent merge of topic2 and topic3 into master, one of the merges should fail since branches diverged
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch t1Ready = new CountDownLatch(1);
    CountDownLatch t2Ready = new CountDownLatch(1);
    AtomicReference<MergeResult> result1 = new AtomicReference<>();
    AtomicReference<MergeResult> result2 = new AtomicReference<>();
    Thread t1 = new Thread(() -> {
      try {
        t1Ready.countDown();
        latch.await();
        result1.set(myMergeSupport.merge(myRoot, "cc69c22bd5d25779e58ad91008e685cbbe7f700a", "refs/heads/master", "merge", new MergeOptions()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    t1.start();
    Thread t2 = new Thread(() -> {
      try {
        t2Ready.countDown();
        latch.await();
        result2.set(myMergeSupport.merge(myRoot, "68b73163526a29a1f5a341f3b6fcd0d928748579", "refs/heads/master", "merge", new MergeOptions()));
      } catch (Exception e) {
        e.printStackTrace();
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
    long successfulMergeCommitCommitCount = changes.stream().filter(m -> m.getParentRevisions().size() == 2).count();

    //either both merges succeeds and made it into repository, or one of them fails
    then(successfulMergeCommitCommitCount == 2 || result1.get().isSuccess() != result2.get().isSuccess())
      .overridingErrorMessage("Non-fast-forward push succeeds")
      .isTrue();
  }
}
