

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.RevisionMatchedByCheckoutRulesCalculator.Result;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class LatestAcceptedRevisionTest extends BaseRemoteRepositoryTest {
  private File myRepo;
  private GitCollectChangesPolicy myCollectChangesPolicy;
  private GitVcsSupport myGit;

  public LatestAcceptedRevisionTest() {
    super("repo_for_checkout_rules");
  }


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    TestLogger logger = new TestLogger();
    logger.setLogLevel(Level.INFO);
    PluginConfigBuilder pluginConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()))
      .setFetchAllRefsEnabled(true);
    myRepo = getRemoteRepositoryDir("repo_for_checkout_rules");

    GitSupportBuilder gitSupportBuilder = gitSupport().withPluginConfig(pluginConfig.build());
    myGit = gitSupportBuilder.build();

    setInternalProperty(GitCollectChangesPolicy.REVISION_BY_CHECKOUT_RULES_USE_LOG_COMMAND, "true");
  }

  @AfterMethod
  @Override
  public void tearDown() {
    myCollectChangesPolicy = null;
    myGit = null;
    super.tearDown();
  }

  @Test(dataProvider = "nativeGit")
  public void test_include_all_exclude_all(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:."),
                                                                "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf", "refs/heads/master",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("-:."),
                                                         "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf", "refs/heads/master",
                                                         Collections.emptySet());
    then(rev.getRevision()).isNull();
  }

  @NotNull
  private Result computeRevisionByCheckoutRulesWithEnabledCache(@NotNull VcsRoot root,
                                                                @NotNull CheckoutRules rules,
                                                                @NotNull String startRevision,
                                                                @NotNull String branchName,
                                                                @NotNull Collection<String> stopRevisions) throws VcsException {
    enableResultCaching();

    Result result = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, rules, startRevision, branchName, stopRevisions);
    // compute one more time using the cached value
    Result fromCache = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, rules, startRevision, branchName, stopRevisions);
    then(result.getRevision()).isEqualTo(fromCache.getRevision());
    then(result.getReachableStopRevisions()).isEqualTo(fromCache.getReachableStopRevisions());
    return result;
  }

  @Test(dataProvider = "nativeGit")
  public void test_start_and_stop_are_the_same(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                                "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf", "refs/heads/master",
                                                                Collections.singleton("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf"));
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");
  }

  @Test(dataProvider = "nativeGit")
  public void test_start_and_stop_are_not_in_repository(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                                "94f6d9029650d88a96e7785d9bc672408bb6e076", "refs/heads/master",
                                                                Collections.singleton("e45f42c7cdcc2d3433e4542cca8f0e2c46d06489"));
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).isEmpty();
  }

  private void ensureFetchPerformed(@NotNull final VcsRoot root, @NotNull String branchName, @NotNull String branchTipRevision) throws VcsException {
    getCollectChangesPolicy().collectChanges(root, RepositoryStateData.createVersionState(branchName, Collections.emptyMap()),
                                             RepositoryStateData.createVersionState(branchName, Collections.singletonMap(branchName, branchTipRevision)),
                                             CheckoutRules.DEFAULT);
  }

  @Test(dataProvider = "nativeGit")
  public void test_search_by_path(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    ensureFetchPerformed(root, "refs/heads/br1", "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78");
    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78", "refs/heads/br1",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("a4bc5909156143a5590adadb2c20eaf71f2a3f8f");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                         "bb6ab65d23fa0ffbaa61d44c8241f127cf0f323f", "refs/heads/br1",
                                                         Collections.emptySet());
    then(rev.getRevision()).isEqualTo("b265fd1608fe17f912a031312e1efc758c4e8a35");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"), "b265fd1608fe17f912a031312e1efc758c4e8a35", "refs/heads/br1",
                                                         Collections.singleton("d5a9a3c51fd53b1aec5e3746f521dc78355d7c78"));
    then(rev.getRevision()).isNull();

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                         "b265fd1608fe17f912a031312e1efc758c4e8a35", "refs/heads/br1",
                                                         Collections.singleton(
                                           "a4bc5909156143a5590adadb2c20eaf71f2a3f8f"));
    then(rev.getRevision()).isEqualTo("a4bc5909156143a5590adadb2c20eaf71f2a3f8f");
  }

  @Test(dataProvider = "nativeGit")
  public void branch_merged_to_master(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "b304522994197be5f336d58cc34edc11cbda095e", "refs/heads/master",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("bb6ab65d23fa0ffbaa61d44c8241f127cf0f323f");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                         "b304522994197be5f336d58cc34edc11cbda095e", "refs/heads/master",
                                                         Collections.emptySet());
    then(rev.getRevision()).isEqualTo("b265fd1608fe17f912a031312e1efc758c4e8a35");
  }

  @Test(dataProvider = "nativeGit")
  public void master_merged_to_branch(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "9c191865e2f2b05727e067aa4f918f3ed54f1f1a", "refs/heads/br2",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("338563d3115318d610ad54839cab287e94b18925");
  }

  @Test(dataProvider = "nativeGit")
  public void both_parents_of_merge_are_interesting(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                                "0ce2e3b06b628633f7b8f73ce634ece1cfe25534", "refs/heads/master",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("a37f9e92344bd037787a98b1f7c8f80ade6d5b68");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test"),
                                                         "a37f9e92344bd037787a98b1f7c8f80ade6d5b68", "refs/heads/master",
                                                         Collections.emptySet());
    then(rev.getRevision()).isEqualTo("a37f9e92344bd037787a98b1f7c8f80ade6d5b68");
  }

  @Test(dataProvider = "nativeGit")
  public void both_parents_of_merge_are_interesting_latest_parents_change_non_interesting_files(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "ce92302a768ce0763e83aebf8c0e16e102c8d06b", "refs/heads/br4",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("d036d012385a762568a474b57337b9cf398b96e0");
  }

  @Test(dataProvider = "nativeGit")
  public void traverse_through_merges_looking_for_interesting_commit(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/subDir"),
                                                                                    "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                                    Collections.emptySet(),
                                                                                    visited);
    then(rev.getRevision()).isEqualTo("be6e6b68e84b5aec8a022a8b2d740ed39a7c63b9");
    then(visited).containsOnly("6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
                               "eea4a3e48901ba036998c9fe0afdc78cc8a05a33",
                               "1330f191b990a389459e28f8754c913e9b417c93",
                               "75c9325d5b129f299fba8567f0fd7f599d336e8f",
                               "be6e6b68e84b5aec8a022a8b2d740ed39a7c63b9");

    visited.clear();
    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile4.java"),
                                                                             "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                             Collections.emptySet(),
                                                                             visited);
    then(rev.getRevision()).isEqualTo("40224a053e16145562d1befa3d0a127c54f5dbff");
    then(visited).contains("6ff32b16fe485e7a0a1e209bf10987e1ad46292e",
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
  @Test(dataProvider = "nativeGit")
  public void merge_commit_tree_does_not_have_difference_with_parents(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master",
                                                                Collections.emptySet());
    then(rev.getRevision()).isEqualTo("658e25230fd75975a2491945ac2664e10aec4f23");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src/File6.java"), "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master", Collections.emptySet());
    then(rev.getRevision()).isEqualTo("a9a11243032a529274e7d8599ba8a6bf55a89e91");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src/File7.java"),
                                                         "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master",
                                                         Collections.emptySet());
    then(rev.getRevision()).isEqualTo("45f1b9531036c9f700cd21c24c1e61cedc44f5a1");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test/TestFile5.java"), "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master", Collections.emptySet());
    then(rev.getRevision()).isEqualTo("8fc8c2a8baf37a71a2cdd0c2b0cd1eedfd1649e8");
  }

  @Test(dataProvider = "nativeGit")
  public void return_reachable_and_visited_stop_revisions_only1(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src"),
                                                                "cb3c3789d8b85d55197069c7c02f5ce693327ee4", "refs/heads/master",
                                                                Arrays.asList("e19e0ffec0a1512674db95ade28047fbfba76fdf", "7e4a8739b038b5b3e551c96dc3a2ef6320772969"));
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("e19e0ffec0a1512674db95ade28047fbfba76fdf");
  }

  @Test(dataProvider = "nativeGit")
  public void return_reachable_and_visited_stop_revisions_only2(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:non-existing-path"),
                                                                "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                Arrays.asList("41cd95e336799f13b7565328da2f344567c23c9f",
                                                              "1b753aa48a20580c26730300ba9a6fee8694b0ca",
                                                              "eea4a3e48901ba036998c9fe0afdc78cc8a05a33",
                                                              "7c56bdca06b531bc0c923e857514a400b83d2e26"));
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("eea4a3e48901ba036998c9fe0afdc78cc8a05a33", "7c56bdca06b531bc0c923e857514a400b83d2e26");
  }

  @Test(dataProvider = "nativeGit")
  public void invalid_stop_revisions_should_be_ignored(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src/File5.java"),
                                                                "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                Arrays.asList("75c9325d5b129f299fba8567f0fd7f599d336e8f",
                                                              "0000000000000000000000000000000000000000"));
    then(rev.getRevision()).isEqualTo("75c9325d5b129f299fba8567f0fd7f599d336e8f");
    then(rev.getReachableStopRevisions()).containsOnly("75c9325d5b129f299fba8567f0fd7f599d336e8f");
  }

  @Test(dataProvider = "nativeGit")
  public void should_return_nearest_stop_revision(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT).build();

    Result rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:src/File4.java"),
                                                                "ce92302a768ce0763e83aebf8c0e16e102c8d06b", "refs/heads/br4",
                                                                Arrays.asList("40224a053e16145562d1befa3d0a127c54f5dbff", "657e07b56a174f14c1925f16a967135f9d494401"));

    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("40224a053e16145562d1befa3d0a127c54f5dbff");

    rev = computeRevisionByCheckoutRulesWithEnabledCache(root, new CheckoutRules("+:test/subDir"),
                                                                "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/br4",
                                                                Arrays.asList("75c9325d5b129f299fba8567f0fd7f599d336e8f", "d036d012385a762568a474b57337b9cf398b96e0"));

    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("75c9325d5b129f299fba8567f0fd7f599d336e8f");
  }

  @Test(dataProvider = "nativeGit")
  public void merge_with_resolved_conflict(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    VcsRoot root = getVcsRootBuilder().withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT).build();

    final Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src/File9.java"),
                                                                                    "f7a3bf64d3522fae44359fe2f4d24326475b3d8e", "refs/heads/master",
                                                                                    Arrays.asList("cb3c3789d8b85d55197069c7c02f5ce693327ee4"),
                                                                                    visited);

    then(rev.getRevision()).isEqualTo("f7a3bf64d3522fae44359fe2f4d24326475b3d8e");
    then(visited).containsOnly("f7a3bf64d3522fae44359fe2f4d24326475b3d8e");
  }

  @Test(dataProvider = "nativeGit")
  public void checked_commits_limit(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    setInternalProperty(CheckoutRulesRevWalk.TEAMCITY_MAX_CHECKED_COMMITS_PROP, "7");
    VcsRoot root = getVcsRootBuilder().build();

    final Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                                    "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                                    Collections.emptySet(),
                                                                                    visited);
    then(rev.getRevision()).isNull();
    then(visited.size()).isEqualTo(7);
  }

  @Test(dataProvider = "nativeGit")
  public void using_cached_value_no_stop_revisions(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    enableResultCaching();
    setInternalProperty(CheckoutRulesRevWalk.TEAMCITY_MAX_CHECKED_COMMITS_PROP, "5");
    VcsRoot root = getVcsRootBuilder().build();

    final Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                                    "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                                    Collections.emptySet(),
                                                                                    visited);
    then(rev.getRevision()).isNull();
    then(visited).hasSize(5);

    visited.clear();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                             "6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "refs/heads/master",
                                                                             Collections.emptySet(),
                                                                             visited);
    then(rev.getRevision()).isNull();
    then(visited).isEmpty();

    visited.clear();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                             "6d8cc5e06db390a20f5b2bf278206a0ec47f05dc", "refs/heads/master",
                                                                             Collections.emptySet(),
                                                                             visited);
    then(rev.getRevision()).isNull();
    then(visited).containsOnly("6d8cc5e06db390a20f5b2bf278206a0ec47f05dc", "7b4fe56d180eba5f22b88a6db13cd026a9af041e", "6ff32b16fe485e7a0a1e209bf10987e1ad46292e");
  }

  @Test(dataProvider = "nativeGit")
  public void using_cached_value_with_stop_revisions(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    enableResultCaching();
    VcsRoot root = getVcsRootBuilder().build();

    final Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                                    "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master",
                                                                                    Arrays.asList("6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "a9a11243032a529274e7d8599ba8a6bf55a89e91", "6d8cc5e06db390a20f5b2bf278206a0ec47f05dc"),
                                                                                    visited);
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("a9a11243032a529274e7d8599ba8a6bf55a89e91");
    then(visited).hasSize(9);

    visited.clear();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                                    "6399724fac6ec9c62e8795fc037ad385e873911f", "refs/heads/master",
                                                                                    Arrays.asList("6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "a9a11243032a529274e7d8599ba8a6bf55a89e91", "6d8cc5e06db390a20f5b2bf278206a0ec47f05dc"),
                                                                                    visited);
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("a9a11243032a529274e7d8599ba8a6bf55a89e91");
    then(visited).isEmpty();

    visited.clear();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:non-existing-path"),
                                                                                    "a2b61002b849eeff94900ba4ddfae4aeb5ea7ded", "refs/heads/master",
                                                                                    Arrays.asList("6ff32b16fe485e7a0a1e209bf10987e1ad46292e", "a9a11243032a529274e7d8599ba8a6bf55a89e91", "6d8cc5e06db390a20f5b2bf278206a0ec47f05dc"),
                                                                                    visited);
    then(rev.getRevision()).isNull();
    then(rev.getReachableStopRevisions()).containsOnly("a9a11243032a529274e7d8599ba8a6bf55a89e91");
    then(visited).containsOnly("6399724fac6ec9c62e8795fc037ad385e873911f", "a2b61002b849eeff94900ba4ddfae4aeb5ea7ded", "bca91c783ab7431c83f2b8ebe0e45381662cf33b", "e19e0ffec0a1512674db95ade28047fbfba76fdf", "7e4a8739b038b5b3e551c96dc3a2ef6320772969");
  }

  @Test(dataProvider = "nativeGit")
  public void using_cached_value_with_stop_revisions_and_result(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));
    enableResultCaching();
    VcsRoot root = getVcsRootBuilder().build();

    final Set<String> visited = new HashSet<>();
    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile5.java"),
                                                                                    "bca91c783ab7431c83f2b8ebe0e45381662cf33b", "refs/heads/master",
                                                                                    Arrays.asList("1c143dd503f3abe5bf17a01f003ca4f601a6473c"),
                                                                                    visited);
    then(rev.getRevision()).isEqualTo("8fc8c2a8baf37a71a2cdd0c2b0cd1eedfd1649e8");
    then(rev.getReachableStopRevisions()).isEmpty();
    then(visited).hasSize(5);

    visited.clear();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile5.java"),
                                                                                    "e19e0ffec0a1512674db95ade28047fbfba76fdf", "refs/heads/master",
                                                                                    Arrays.asList("1c143dd503f3abe5bf17a01f003ca4f601a6473c"),
                                                                                    visited);
    then(rev.getRevision()).isEqualTo("8fc8c2a8baf37a71a2cdd0c2b0cd1eedfd1649e8");
    then(rev.getReachableStopRevisions()).isEmpty();
    then(visited).hasSize(2);
  }

  private void enableResultCaching() {
    setInternalProperty(CheckoutRulesLatestRevisionCache.CHECKOUT_RULES_REVISION_CACHE_ENABLED, "true");
  }

  @Test(dataProvider = "true,false")
  public void using_cached_value_with_stop_revisions_and_result_cached_start_revision_is_unreachable1(boolean cacheEnabled) throws VcsException {
    setInternalProperty(CheckoutRulesLatestRevisionCache.CHECKOUT_RULES_REVISION_CACHE_ENABLED, String.valueOf(cacheEnabled));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:README"),
                                                                                    "3d0b399a7119f88f08effe84b46b34301532486c", "refs/heads/master",
                                                                                    Arrays.asList("3dada99f39b112fe1de4da19a6ed5113f0035f21"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("05bf0e98112db7ee945ebe428d89c2b8fab7f036");
    then(rev.getReachableStopRevisions()).isEmpty();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:README"),
                                                                                    "bca91c783ab7431c83f2b8ebe0e45381662cf33b", "refs/heads/master",
                                                                                    Arrays.asList("3dada99f39b112fe1de4da19a6ed5113f0035f21"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("7b4fe56d180eba5f22b88a6db13cd026a9af041e");
    then(rev.getReachableStopRevisions()).isEmpty();
  }

  @Test(dataProvider = "true,false")
  public void using_cached_value_with_stop_revisions_and_result_cached_start_revision_is_unreachable2(boolean cacheEnabled) throws VcsException {
    setInternalProperty(CheckoutRulesLatestRevisionCache.CHECKOUT_RULES_REVISION_CACHE_ENABLED, String.valueOf(cacheEnabled));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:README"),
                                                                                    "a2b61002b849eeff94900ba4ddfae4aeb5ea7ded", "refs/heads/master",
                                                                                    Arrays.asList("1c143dd503f3abe5bf17a01f003ca4f601a6473c"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("e19e0ffec0a1512674db95ade28047fbfba76fdf");
    then(rev.getReachableStopRevisions()).isEmpty();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:README"),
                                                                                    "3d0b399a7119f88f08effe84b46b34301532486c", "refs/heads/master",
                                                                                    Arrays.asList("1c143dd503f3abe5bf17a01f003ca4f601a6473c"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("05bf0e98112db7ee945ebe428d89c2b8fab7f036");
    then(rev.getReachableStopRevisions()).isEmpty();
  }

  @Test(dataProvider = "true,false")
  public void using_cached_value_with_stop_revisions_and_result_cached_start_revision_is_unreachable3(boolean cacheEnabled) throws VcsException {
    setInternalProperty(CheckoutRulesLatestRevisionCache.CHECKOUT_RULES_REVISION_CACHE_ENABLED, String.valueOf(cacheEnabled));
    VcsRoot root = getVcsRootBuilder().build();

    Result rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile7.java"),
                                                                                    "7e4a8739b038b5b3e551c96dc3a2ef6320772969", "refs/heads/master",
                                                                                    Arrays.asList("6d8cc5e06db390a20f5b2bf278206a0ec47f05dc"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("1c143dd503f3abe5bf17a01f003ca4f601a6473c");
    then(rev.getReachableStopRevisions()).isEmpty();

    rev = getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test/TestFile7.java"),
                                                                                    "3d0b399a7119f88f08effe84b46b34301532486c", "refs/heads/master",
                                                                                    Arrays.asList("6d8cc5e06db390a20f5b2bf278206a0ec47f05dc"),
                                                                                    null);
    then(rev.getRevision()).isEqualTo("1c143dd503f3abe5bf17a01f003ca4f601a6473c");
    then(rev.getReachableStopRevisions()).isEmpty();
  }

  @NotNull
  private GitCollectChangesPolicy getCollectChangesPolicy() {
    if (myCollectChangesPolicy == null) {
      myCollectChangesPolicy = myGit.getCollectChangesPolicy();
    }
    return myCollectChangesPolicy;
  }

  private VcsRootBuilder getVcsRootBuilder() {
    return vcsRoot().withFetchUrl(GitUtils.toURL(myRepo));
  }
}