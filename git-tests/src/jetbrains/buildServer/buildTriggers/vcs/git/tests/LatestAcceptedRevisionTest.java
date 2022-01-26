/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class LatestAcceptedRevisionTest extends BaseRemoteRepositoryTest {
  private PluginConfigBuilder myConfig;
  private File myRepo;

  public LatestAcceptedRevisionTest() {
    super("repo_for_checkout_rules");
  }


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    TestLogger logger = new TestLogger();
    logger.setLogLevel(Level.INFO);
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    myRepo = getRemoteRepositoryDir("repo_for_checkout_rules");
  }

  public void test_include_all_exclude_all() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:."),
                                                                                       "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("-:."),
                                                                                     "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                     Collections.emptySet());
    then(rev).isNull();
  }

  public void test_start_and_stop_are_the_same() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                       "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                            Collections.singleton("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf"));
    then(rev).isNull();
  }

  public void test_start_and_stop_are_not_in_repository() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                       "94f6d9029650d88a96e7785d9bc672408bb6e076",
                                                                                            Collections.singleton("e45f42c7cdcc2d3433e4542cca8f0e2c46d06489"));
    then(rev).isNull();
  }

  private void ensureFetchPerformed(@NotNull final GitVcsSupport support, @NotNull final VcsRoot root, @NotNull String branchName, @NotNull String branchTipRevision) throws VcsException {
    support.getCollectChangesPolicy().collectChanges(root, RepositoryStateData.createVersionState(branchName, Collections.emptyMap()),
                                                     RepositoryStateData.createVersionState(branchName, Collections.singletonMap(branchName, branchTipRevision)),
                                                     CheckoutRules.DEFAULT);
  }

  public void test_search_by_path() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/br1", "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("a4bc5909156143a5590adadb2c20eaf71f2a3f8f");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                     "bb6ab65d23fa0ffbaa61d44c8241f127cf0f323f",
                                                                                     Collections.emptySet());
    then(rev).isEqualTo("b265fd1608fe17f912a031312e1efc758c4e8a35");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                     "b265fd1608fe17f912a031312e1efc758c4e8a35",
                                                                                     Collections.singleton(
                                                                                       "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78"));
    then(rev).isNull();

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                     "b265fd1608fe17f912a031312e1efc758c4e8a35",
                                                                                     Collections.singleton(
                                                                                       "a4bc5909156143a5590adadb2c20eaf71f2a3f8f"));
    then(rev).isEqualTo("a4bc5909156143a5590adadb2c20eaf71f2a3f8f");
  }

  public void branch_merged_to_master() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "b304522994197be5f336d58cc34edc11cbda095e");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "b304522994197be5f336d58cc34edc11cbda095e",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("bb6ab65d23fa0ffbaa61d44c8241f127cf0f323f");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                     "b304522994197be5f336d58cc34edc11cbda095e",
                                                                                     Collections.emptySet());
    then(rev).isEqualTo("b265fd1608fe17f912a031312e1efc758c4e8a35");
  }

  public void master_merged_to_branch() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/br2", "9c191865e2f2b05727e067aa4f918f3ed54f1f1a");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "9c191865e2f2b05727e067aa4f918f3ed54f1f1a",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("338563d3115318d610ad54839cab287e94b18925");
  }

  public void both_parents_of_merge_are_interesting() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "0ce2e3b06b628633f7b8f73ce634ece1cfe25534");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                            "0ce2e3b06b628633f7b8f73ce634ece1cfe25534",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("a37f9e92344bd037787a98b1f7c8f80ade6d5b68");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                            "a37f9e92344bd037787a98b1f7c8f80ade6d5b68",
                                                                                     Collections.emptySet());
    then(rev).isEqualTo("a37f9e92344bd037787a98b1f7c8f80ade6d5b68");
  }

  public void both_parents_of_merge_are_interesting_latest_parents_change_non_interesting_files() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/br4", "ce92302a768ce0763e83aebf8c0e16e102c8d06b");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "ce92302a768ce0763e83aebf8c0e16e102c8d06b",
                                                                                            Collections.emptySet());
    then(rev).isEqualTo("d036d012385a762568a474b57337b9cf398b96e0");
  }

  public void traverse_through_merges_looking_for_interesting_commit() throws VcsException, IOException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "6d8cc5e06db390a20f5b2bf278206a0ec47f05dc");
    Set<String> visited = new HashSet<>();
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/subDir"),
                                                                                            "6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
                                                                                            Collections.emptySet(),
                                                                                            visited);
    then(rev).isEqualTo("be6e6b68e84b5aec8a022a8b2d740ed39a7c63b9");
    then(visited).containsOnly("6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
                               "eea4a3e48901ba036998c9fe0afdc78cc8a05a33",
                               "1330f191b990a389459e28f8754c913e9b417c93",
                               "75c9325d5b129f299fba8567f0fd7f599d336e8f",
                               "be6e6b68e84b5aec8a022a8b2d740ed39a7c63b9");

    visited.clear();
    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile4.java"),
                                                                                     "6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
                                                                                     Collections.emptySet(),
                                                                                     visited);
    then(rev).isEqualTo("40224a053e16145562d1befa3d0a127c54f5dbff");
    then(visited).containsOnly("6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
                               "ce92302a768ce0763e83aebf8c0e16e102c8d06b",
                               "d036d012385a762568a474b57337b9cf398b96e0",
                               "40224a053e16145562d1befa3d0a127c54f5dbff");
  }

  /**
   * This test is for the following case:
   * <pre>
   *       o m3
   *     /  \
   * m1 o    o m2
   *    | \/ |
   * c1 o   o c2
   *    |  /
   *    o
   * </pre>
   * Here m1 and m2 have the same trees, so when m3 is created there is no diff between m3 & m1 and m3 & m2.
   * So although both c1 & c2 change interesting files we won't see a diff in trees in m3 comparing to its parents and
   * can think that nothing interesting was changed, while this is not true.
   */
  public void merge_commit_tree_does_not_have_difference_with_parents() throws VcsException, IOException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "6399724fac6ec9c62e8795fc037ad385e873911f");

    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "6399724fac6ec9c62e8795fc037ad385e873911f",
                                                                                            Collections.emptySet(),
                                                                                            null);
    then(rev).isEqualTo("6394695f179d87f7f5fc712e12dfac0ed0d98652"); // m3 is 6394695f179d87f7f5fc712e12dfac0ed0d98652

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src/File6.java"),
                                                                                            "6399724fac6ec9c62e8795fc037ad385e873911f",
                                                                                            Collections.emptySet(),
                                                                                            null);
    then(rev).isEqualTo("6394695f179d87f7f5fc712e12dfac0ed0d98652");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src/File7.java"),
                                                                                            "6399724fac6ec9c62e8795fc037ad385e873911f",
                                                                                            Collections.emptySet(),
                                                                                            null);
    then(rev).isEqualTo("6394695f179d87f7f5fc712e12dfac0ed0d98652");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile5.java"),
                                                                                            "6399724fac6ec9c62e8795fc037ad385e873911f",
                                                                                            Collections.emptySet(),
                                                                                            null);
    then(rev).isEqualTo("8fc8c2a8baf37a71a2cdd0c2b0cd1eedfd1649e8");

  }

  @NotNull
  private GitVcsSupport git() {
    return gitSupport().withPluginConfig(myConfig).build();
  }
}
