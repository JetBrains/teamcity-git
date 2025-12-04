package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.apache.log4j.Level;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;
import static org.assertj.core.api.BDDAssertions.then;

@Test
@TestFor(issues = "TW-97575")
public class LimitedChangesCollectionTest extends BaseRemoteRepositoryTest {
  private PluginConfigBuilder myConfig;
  private File myRepo;

  private static final String[] refsHeadsMainRevisions = {
    "ae69582618328b94fa619727b37bfc33243cbfb1", //  main: change 1
    "41f8799459e5c6745ef6f4db317102358636345f", //  main: change 2
    "242b64b175b137377b160837daf5157f2f0482f8", //  main: change 3
    "b11c9491dc17d5783917d8525f96e517f17ba8f7", //  main: change 4
    "46b404e8f3548d152479fb96cfb09c3dec1d0fe3", //  main: change 5
    "df610548e3c8c36121bfadee95fac413838d51be", //  main: change 6
    "793ceb956574dfe566fc3022a17bf6e8ba789fd3", //  main: change 7
    "605b07eb57c6b54f7b3ccebe5edd8a1d775b54c6", //  main: change 8
    "4899ed14c5cb039309a41b3420e276a797ff4617", //  main: change 9
    "98d138a112bd47cee314e9b5f06ad8956f288215", //  main: change 10
  };

  private static final String[] refsRewritten1MainRevisions = {
    "10c282dcfd7f28e8af6d482ec53e7b813d7d2043", // main: change 1 rewritten commit
    "70dd3fa2367bf4d44c02ae3191edf2c1ab9c9296", // main: change 2 rewritten commit
    "e344f99e4155863c6a7b6aac1cdf09a6214f8014", // main: change 3 rewritten commit
    "14990765f0db05052bb50b3b5fd1c9a8d2161a9d", // main: change 4 rewritten commit
    "62734c6c693c89fecda8b047d263f121a999f947", // main: change 5 rewritten commit
    "50a2174fcc3413af39e07f939000d2d6361d98ed", // main: change 6 rewritten commit
    "9ddf9c91824de43ee0dd97e652db2f2251468936", // main: change 7 rewritten commit
    "6aa844f4f3a8104e09c2299ea974584855282439", // main: change 8 rewritten commit
    "3c266ae4ec75e841f7819dacc76319770588179b", // main: change 9 rewritten commit
    "0dbda778c5afddfcd892465c98fe7c84cdd81fcb"  // main: change 10 rewritten commit
  };

  private static final String[] refsNamespace1Ignored1Revisions = {
    "ae69582618328b94fa619727b37bfc33243cbfb1", // main: change 1
    "41f8799459e5c6745ef6f4db317102358636345f", // main: change 2
    "242b64b175b137377b160837daf5157f2f0482f8", // main: change 3
    "b11c9491dc17d5783917d8525f96e517f17ba8f7", // main: change 4
    "46b404e8f3548d152479fb96cfb09c3dec1d0fe3", // main: change 5
    "7ab9e313d00f9a275b2517389e523cc229d96a5f", // branch-1: change 1
    "0971ef2b9b55ffedb10c527ea781307fd218fadb", // (branch-1) branch-1: change 2
    "e8262c8645fb1e9d245f8c7fd067c76a2ea57ad2", // ignored-1: change 1
    "80515d8725506331714a0a8e89dbb8d8c65501be", // ignored-1: change 2
    "02151a939e5d0a0cfdb652bfffa7be77cafbc48e" // Merge branch 'branch-1' into ignored-1
  };

  public LimitedChangesCollectionTest() {
    super("TW-97575-report-only-heads-as-changes");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    TestLogger logger = new TestLogger();
    logger.setLogLevel(Level.INFO);
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    myRepo = getRemoteRepositoryDir("TW-97575-report-only-heads-as-changes");
  }

  private GitVcsSupport git() {
    return gitSupport().withPluginConfig(myConfig).build();
  }

  @Test
  public void internal_property_is_parsed_correctly() {
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/heads/main");
    then(myConfig.build().getPrefixesToCollectOnlyHeads()).containsExactly("refs/heads/main");

    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs;heads;main;");
    then(myConfig.build().getPrefixesToCollectOnlyHeads()).containsExactly("refs", "heads", "main");

    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "");
    then(myConfig.build().getPrefixesToCollectOnlyHeads()).isEmpty();

    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, ";;;;");
    then(myConfig.build().getPrefixesToCollectOnlyHeads()).isEmpty();
  }

  @Test
  public void report_only_head_of_single_branch_when_in_limited_namespace() throws IOException, VcsException {
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/heads/main");

    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/main").build();

    List<ModificationData> changes = myGit.collectChanges(root, refsHeadsMainRevisions[0], refsHeadsMainRevisions[3], CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(1);
    then(changes).extracting("version").containsExactly(refsHeadsMainRevisions[3]);

    changes = myGit.collectChanges(root, refsHeadsMainRevisions[3], refsHeadsMainRevisions[6], CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(1);
    then(changes).extracting("version").containsExactly(refsHeadsMainRevisions[6]);

    changes = myGit.collectChanges(root, refsHeadsMainRevisions[6], refsHeadsMainRevisions[9], CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(1);
    then(changes).extracting("version").containsExactly(refsHeadsMainRevisions[9]);
  }

  @Test
  public void rewritten_branch_limited_and_main() throws IOException, VcsException {
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/rewritten-1");

    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    RepositoryStateData fromState =
      createVersionState("refs/heads/main", map("refs/heads/main", refsHeadsMainRevisions[0], "refs/rewritten-1/main", refsRewritten1MainRevisions[0]));
    RepositoryStateData toState = createVersionState("refs/heads/main", map("refs/heads/main", refsHeadsMainRevisions[4], "refs/rewritten-1/main", refsRewritten1MainRevisions[4]));
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(5);
    then(changes).extracting("version")
                 .containsAll(Stream.concat(
                   Stream.of(refsRewritten1MainRevisions[4]),
                   Arrays.stream(refsHeadsMainRevisions, 1, 5)
                 ).collect(Collectors.toList()));

    fromState = createVersionState("refs/heads/main",
                                   map("refs/heads/main", refsHeadsMainRevisions[4], "refs/rewritten-1/main", refsRewritten1MainRevisions[4]));
    toState = createVersionState("refs/heads/main",
                                 map("refs/heads/main", refsHeadsMainRevisions[9], "refs/rewritten-1/main", refsRewritten1MainRevisions[9]));
    changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(6);
    then(changes).extracting("version")
                 .containsAll(Stream.concat(Arrays.stream(refsHeadsMainRevisions, 5, 10),
                                            Stream.of(refsRewritten1MainRevisions[9])).collect(Collectors.toList()));
  }

  /**
   * A -> B -> C -> D (main): must collect BCD
   * ^
   * shared-1
   */
  @Test
  public void limited_branch_pointing_to_unlimited_restricts_changes_collection() throws IOException, VcsException {
    // get current state does not respect the real branch spec
    List<String> branchSpec = Arrays.asList("refs/heads/main", "refs/shared-1/shared-1");
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/shared-1");
    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/main").build();

    RepositoryStateData fromState =
      createVersionState("refs/heads/main", map("refs/heads/main", refsHeadsMainRevisions[0]));
    RepositoryStateData toState = createVersionState("refs/heads/main", Maps.filterKeys(myGit.getCurrentState(root).getBranchRevisions(), (key -> branchSpec.contains(key))));
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);
    then(changes.size()).isEqualTo(6);
    then(changes).extracting("version").containsAll(Arrays.stream(refsHeadsMainRevisions, 4, 10).collect(Collectors.toList()));
  }

  /**
   * A -> B -> C -> D (ignored-1): must collect D
   *      ^
   *    ignored-2
   * Ideally, we'd want to collect BD, but marking "C" as uninteresting propagates this flag to B as well
   */
  @Test
  public void limited_branch_pointing_to_earlier_commit_in_limited_branch_is_collected() throws IOException, VcsException {
    List<String> branchSpec = Arrays.asList("refs/namespace-1/ignored-1", "refs/namespace-1/ignored-2", "refs/heads/branch-1");
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/namespace-1");
    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/main").build();

    RepositoryStateData fromState =
      createVersionState("refs/heads/main", map("refs/heads/main", refsHeadsMainRevisions[0]));
    RepositoryStateData toState = createVersionState("refs/heads/main", Maps.filterKeys(myGit.getCurrentState(root).getBranchRevisions(), (key -> branchSpec.contains(key))));
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);

    then(changes.size()).isEqualTo(1);
    then(changes).extracting("version").containsAll(Arrays.asList(refsNamespace1Ignored1Revisions[9]));
  }

  /**
   * A -> B -> C -> F -> G (ignored-1): must collect G
   *            \      /
   *             D -> E (branch-1)
   * <br>
   * Ideally, we'd want to collect ABCDEG, but jgit propagates the uninteresting flag to the parents.
   */
  @Test
  public void limited_branch_with_not_limited_parent_does_restirct_parent() throws IOException, VcsException {
    List<String> branchSpec = Arrays.asList("refs/namespace-1/ignored-1", "refs/heads/branch-1");
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/namespace-1");
    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/branch-1").build();

    RepositoryStateData fromState = createVersionState("refs/heads/branch-1", refsNamespace1Ignored1Revisions[0]);
    RepositoryStateData toState = createVersionState("refs/heads/branch-1", Maps.filterKeys(myGit.getCurrentState(root).getBranchRevisions(), (key -> branchSpec.contains(key))));
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);

    then(changes.size()).isEqualTo(1);
    then(changes).extracting("version").containsAll(Arrays.asList(refsNamespace1Ignored1Revisions[9]));
  }

  /**
   * A -> B -> C -> D (main): must collect ABCD
   *                ^
   *             shared-2
   */
  @Test
  public void unrestricted_when_limited_and_unlimited_point_to_same_revision() throws IOException, VcsException {
    List<String> branchSpec = Arrays.asList("refs/shared-1/shared-2", "refs/heads/main");
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/shared-1");
    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/main").build();

    RepositoryStateData fromState = createVersionState("refs/heads/main", refsHeadsMainRevisions[0]);
    RepositoryStateData toState = createVersionState("refs/heads/main", Maps.filterKeys(myGit.getCurrentState(root).getBranchRevisions(), (key -> branchSpec.contains(key))));
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);

    then(changes.size()).isEqualTo(9);
    then(changes).extracting("version").containsAll(Arrays.stream(refsHeadsMainRevisions, 1, 10).collect(Collectors.toList()));
  }

  @Test
  public void unchanged_limited_branch_is_not_reported_as_change() throws IOException, VcsException {
    setInternalProperty(PluginConfigImpl.COLLECT_ONLY_HEADS_FOR_PREFIXES, "refs/heads/main");
    GitVcsSupport myGit = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).withBranch("refs/heads/main").build();

    RepositoryStateData fromState = createVersionState("refs/heads/main", refsHeadsMainRevisions[0]);
    RepositoryStateData toState = createVersionState("refs/heads/main", refsHeadsMainRevisions[0]);
    List<ModificationData> changes = myGit.getCollectChangesPolicy().collectChanges(root, fromState, toState, CheckoutRules.DEFAULT);

    then(changes.size()).isEqualTo(0);
  }
}
