package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCherryPickSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CherryPickOptions;
import jetbrains.buildServer.vcs.CherryPickResult;
import jetbrains.buildServer.vcs.CherryPickSupport;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * The 'merge' repository used by these tests looks as follows:
 * <pre>
 *   master  f727882 (three) &lt;- 2ffada4 (two) &lt;- e03fccd (one) &lt;- 497ad26 (initial), all of them change the file 'a'
 *   topic   080f42b = merge(d2e06a9, f727882)
 *           d2e06a9 (topic.3) &lt;- 6ffbeea (topic.2) &lt;- 0003f16 (topic.1) &lt;- e03fccd, all of them add a line to the file 'b'
 *   topic2  cc69c22 (topic2 3) &lt;- bcbb9d0 (topic2 2) &lt;- d73cf95 (topic2 1) &lt;- f727882, the same for the file 'b'
 *   topic3  68b7316 = merge(f727882, d2e06a9), the first parent is master
 * </pre>
 */
@Test
@TestFor(issues = "TW-102761")
public class GitCherryPickSupportTest extends BaseRemoteRepositoryTest {

  private static final String TOPIC_1 = "0003f1603abfe7fd784b95faa8ae4803598694a0";
  private static final String TOPIC_2 = "6ffbeea7e607c069bdfeea5ea10d7b139c06ecca";
  private static final String TOPIC_3 = "d2e06a930fb98746f2208791e6cd5bb41e57ed3f";
  private static final String TOPIC2_1 = "d73cf95b203b77a4f9ca969ac454e69eaebbf697";
  private static final String TOPIC2_3 = "cc69c22bd5d25779e58ad91008e685cbbe7f700a";
  private static final String MASTER = "f727882267df4f8fe0bc58c18559591918aefc54";
  private static final String MERGE_INTO_MASTER = "68b73163526a29a1f5a341f3b6fcd0d928748579";

  private GitVcsSupport myGit;
  private CherryPickSupport myCherryPickSupport;
  private VcsRoot myRoot;
  private ServerPaths myPaths;
  private File myRemote;

  public GitCherryPickSupportTest() {
    super("merge");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myCherryPickSupport = createCherryPickSupport(gitSupport().withServerPaths(myPaths));
    myRemote = getRemoteRepositoryDir("merge");
    myRoot = vcsRoot().withFetchUrl(myRemote).build();
  }

  @NotNull
  private CherryPickSupport createCherryPickSupport(@NotNull GitSupportBuilder builder) throws Exception {
    myGit = builder.build();
    GitRepoOperationsImpl repoOperations = new GitRepoOperationsImpl(builder.getPluginConfig(),
                                                                    builder.getTransportFactory(),
                                                                    r -> null,
                                                                    (a, b, c) -> {},
                                                                    myKnownHostsManager);
    return new GitCherryPickSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(),
                                    builder.getPluginConfig(), repoOperations);
  }


  public void picks_single_commit_onto_branch() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    String newTip = result.getNewBranchRevision();
    then(newTip).isNotNull().isNotEqualTo(MASTER);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(newTip);
    then(parentRevisions(myRemote, newTip)).containsExactly(MASTER);
    then(fileContent(myRemote, newTip, "b")).isEqualTo("b\n");

    then(result.getPickedCommits()).hasSize(1);
    CherryPickResult.PickedCommit picked = result.getPickedCommits().get(0);
    then(picked.getStatus()).isEqualTo(CherryPickResult.PickedCommit.Status.CREATED);
    then(picked.getSourceRevision()).isEqualTo(TOPIC_1);
    then(picked.getCreatedRevision()).isEqualTo(newTip);
    then(picked.getConflictFiles()).isEmpty();
  }


  public void preserves_author_and_mentions_the_source_revision() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/master", CherryPickOptions.create());

    String newTip = result.getNewBranchRevision();
    then(authorIdent(myRemote, newTip)).isEqualTo(authorIdent(myRemote, TOPIC_1));
    then(committerIdent(myRemote, newTip))
      .overridingErrorMessage("the committer is the VCS root's identity, not the original one")
      .isNotEqualTo(authorIdent(myRemote, TOPIC_1));
    then(fullMessage(myRemote, newTip)).isEqualTo(fullMessage(myRemote, TOPIC_1).trim() +
                                                  "\n\n(cherry picked from commit " + TOPIC_1 + ")\n");
  }


  public void picks_several_commits_and_publishes_them_at_once() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Arrays.asList(TOPIC_1, TOPIC_2, TOPIC_3),
                                                            "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    String newTip = result.getNewBranchRevision();
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(newTip);
    then(fileContent(myRemote, newTip, "b")).isEqualTo("b\nb\nb\n");

    then(sourceRevisions(result)).containsExactly(TOPIC_1, TOPIC_2, TOPIC_3);
    then(statuses(result)).containsExactly(CherryPickResult.PickedCommit.Status.CREATED,
                                          CherryPickResult.PickedCommit.Status.CREATED,
                                          CherryPickResult.PickedCommit.Status.CREATED);
    List<String> created = createdRevisions(result);
    then(created).hasSize(3);
    then(created.get(2)).isEqualTo(newTip);
    //the picked commits form a linear history on top of master
    then(parentRevisions(myRemote, created.get(0))).containsExactly(MASTER);
    then(parentRevisions(myRemote, created.get(1))).containsExactly(created.get(0));
    then(parentRevisions(myRemote, created.get(2))).containsExactly(created.get(1));
  }


  public void skips_the_commit_already_reachable_from_the_destination_branch() throws Exception {
    String topicBefore = resolveRef(myRemote, "refs/heads/topic");

    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/topic", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.ALREADY_PRESENT);
    then(result.getMessage()).contains("already present");
    then(result.getNewBranchRevision())
      .overridingErrorMessage("nothing was published, so there is no new branch revision")
      .isNull();
    then(statuses(result)).containsExactly(CherryPickResult.PickedCommit.Status.ALREADY_PRESENT);
    then(resolveRef(myRemote, "refs/heads/topic")).isEqualTo(topicBefore);
  }


  public void picking_the_same_commit_twice_changes_nothing() throws Exception {
    CherryPickResult first = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/master", CherryPickOptions.create());
    then(first.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);

    CherryPickResult second = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/master", CherryPickOptions.create());

    then(second.getStatus()).isEqualTo(CherryPickResult.Status.ALREADY_PRESENT);
    then(statuses(second)).containsExactly(CherryPickResult.PickedCommit.Status.ALREADY_PRESENT);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(first.getNewBranchRevision());
  }


  public void reports_conflicting_paths_and_publishes_nothing() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_3, "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.CONFLICT);
    then(result.getMessage()).contains(TOPIC_3);
    then(result.getNewBranchRevision()).isNull();
    then(result.getPickedCommits()).hasSize(1);
    CherryPickResult.PickedCommit conflicting = result.getPickedCommits().get(0);
    then(conflicting.getStatus()).isEqualTo(CherryPickResult.PickedCommit.Status.CONFLICT);
    then(conflicting.getSourceRevision()).isEqualTo(TOPIC_3);
    then(conflicting.getConflictFiles()).containsExactly("b");
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);
  }


  public void does_not_publish_a_partially_applied_batch() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    //the first revision applies cleanly, the second one conflicts with it
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Arrays.asList(TOPIC_1, TOPIC2_3),
                                                            "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.CONFLICT);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);
    then(sourceRevisions(result)).containsExactly(TOPIC_1, TOPIC2_3);
    //the commit built for the first revision was discarded together with the operation, so it is not a created one
    then(statuses(result)).containsExactly(CherryPickResult.PickedCommit.Status.PICKABLE,
                                          CherryPickResult.PickedCommit.Status.CONFLICT);
    then(createdRevisions(result)).isEmpty();
  }


  public void reports_the_revisions_after_the_conflicting_one_as_not_attempted() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Arrays.asList(TOPIC_3, TOPIC_1),
                                                            "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.CONFLICT);
    then(sourceRevisions(result)).containsExactly(TOPIC_3, TOPIC_1);
    then(statuses(result)).containsExactly(CherryPickResult.PickedCommit.Status.CONFLICT,
                                          CherryPickResult.PickedCommit.Status.NOT_ATTEMPTED);
  }


  public void picks_merge_commit_with_the_specified_mainline_parent() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Collections.singletonList(MERGE_INTO_MASTER),
                                                            "refs/heads/master",
                                                            CherryPickOptions.create().withMainlineParentNumber(1));

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    String newTip = result.getNewBranchRevision();
    //the changes of the merge commit are replayed as a regular commit with a single parent
    then(parentRevisions(myRemote, newTip)).containsExactly(MASTER);
    then(fileContent(myRemote, newTip, "b")).isEqualTo("b\nb\nb\n");
  }


  public void rejects_merge_commit_without_the_mainline_parent() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, MERGE_INTO_MASTER, "refs/heads/master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.REJECTED);
    then(result.getMessage()).contains("merge commit");
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);
  }


  public void rejects_mainline_parent_out_of_range() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Collections.singletonList(MERGE_INTO_MASTER),
                                                            "refs/heads/master",
                                                            CherryPickOptions.create().withMainlineParentNumber(3));

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.REJECTED);
    then(result.getMessage()).contains("out of range");
  }


  public void reports_missing_destination_branch() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/no-such-branch", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.REJECTED);
    then(result.getMessage()).contains("no-such-branch").contains("doesn't exist");
    then(resolveRef(myRemote, "refs/heads/no-such-branch")).isNull();
  }


  public void reports_unknown_source_revision() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, ObjectId.zeroId().name(), "refs/heads/master",
                                                            CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.REJECTED);
    then(result.getMessage()).isNotNull();
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);
  }


  public void rejects_an_empty_request() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Collections.<String>emptyList(), "refs/heads/master",
                                                            CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.REJECTED);
    then(result.getMessage()).isNotNull();
  }


  public void accepts_short_destination_branch_name() throws Exception {
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "master", CherryPickOptions.create());

    then(result.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(result.getNewBranchRevision());
  }


  public void dry_run_does_not_publish_anything() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    CherryPickResult dryRun = myCherryPickSupport.dryRunCherryPick(myRoot, Arrays.asList(TOPIC_1, TOPIC_2),
                                                                  "refs/heads/master", CherryPickOptions.create());

    then(dryRun.getStatus()).isEqualTo(CherryPickResult.Status.PICKABLE);
    then(dryRun.getNewBranchRevision()).isNull();
    then(sourceRevisions(dryRun)).containsExactly(TOPIC_1, TOPIC_2);
    then(statuses(dryRun)).containsExactly(CherryPickResult.PickedCommit.Status.PICKABLE,
                                           CherryPickResult.PickedCommit.Status.PICKABLE);
    then(createdRevisions(dryRun)).isEmpty();
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);

    //the real operation still works after the dry run
    CherryPickResult result = myCherryPickSupport.cherryPick(myRoot, Arrays.asList(TOPIC_1, TOPIC_2),
                                                            "refs/heads/master", CherryPickOptions.create());
    then(result.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(result.getNewBranchRevision());
  }


  public void dry_run_detects_conflicts() throws Exception {
    String masterBefore = resolveRef(myRemote, "refs/heads/master");

    CherryPickResult dryRun = myCherryPickSupport.dryRunCherryPick(myRoot, Collections.singletonList(TOPIC_3),
                                                                  "refs/heads/master", CherryPickOptions.create());

    then(dryRun.getStatus()).isEqualTo(CherryPickResult.Status.CONFLICT);
    then(dryRun.getPickedCommits().get(0).getConflictFiles()).containsExactly("b");
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(masterBefore);
  }


  public void dry_run_reports_already_present_revisions_the_same_way_as_the_real_call() throws Exception {
    CherryPickResult dryRun = myCherryPickSupport.dryRunCherryPick(myRoot, Collections.singletonList(TOPIC_1),
                                                                  "refs/heads/topic", CherryPickOptions.create());

    then(dryRun.getStatus())
      .overridingErrorMessage("ALREADY_PRESENT takes precedence when there is nothing left to replay, a dry run included")
      .isEqualTo(CherryPickResult.Status.ALREADY_PRESENT);
    then(dryRun.getNewBranchRevision()).isNull();
    then(statuses(dryRun)).containsExactly(CherryPickResult.PickedCommit.Status.ALREADY_PRESENT);
  }


  public void concurrent_cherry_picks_into_the_same_branch() throws Exception {
    //disable retries, so that the loser of the race reports a failure instead of picking again
    myCherryPickSupport = createCherryPickSupport(gitSupport().withPluginConfig(pluginConfig().setPaths(myPaths).setMergeRetryAttempts(0)));

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch ready = new CountDownLatch(2);
    AtomicReference<CherryPickResult> result1 = new AtomicReference<>();
    AtomicReference<CherryPickResult> result2 = new AtomicReference<>();
    Thread t1 = new Thread(() -> {
      try {
        ready.countDown();
        start.await();
        result1.set(myCherryPickSupport.cherryPick(myRoot, TOPIC_1, "refs/heads/master", CherryPickOptions.create()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    Thread t2 = new Thread(() -> {
      try {
        ready.countDown();
        start.await();
        result2.set(myCherryPickSupport.cherryPick(myRoot, TOPIC2_1, "refs/heads/master", CherryPickOptions.create()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    t1.start();
    t2.start();
    ready.await();
    start.countDown();
    t1.join();
    t2.join();

    String masterAfter = resolveRef(myRemote, "refs/heads/master");
    List<String> published = new ArrayList<>();
    for (CherryPickResult result : Arrays.asList(result1.get(), result2.get())) {
      if (result != null && result.getStatus() == CherryPickResult.Status.PICKED)
        published.add(result.getNewBranchRevision());
    }
    //a push is a compare-and-swap against the tip observed before the pick, so a losing thread must not overwrite the winner
    then(published).isNotEmpty();
    then(published.get(published.size() - 1)).isEqualTo(masterAfter);
    for (String revision : published) {
      then(isReachable(myRemote, revision, masterAfter))
        .overridingErrorMessage("Published revision %s is not reachable from the branch tip %s", revision, masterAfter)
        .isTrue();
    }
  }


  @NotNull
  private static List<String> sourceRevisions(@NotNull CherryPickResult result) {
    List<String> revisions = new ArrayList<>();
    for (CherryPickResult.PickedCommit picked : result.getPickedCommits()) {
      revisions.add(picked.getSourceRevision());
    }
    return revisions;
  }

  @NotNull
  private static List<CherryPickResult.PickedCommit.Status> statuses(@NotNull CherryPickResult result) {
    List<CherryPickResult.PickedCommit.Status> statuses = new ArrayList<>();
    for (CherryPickResult.PickedCommit picked : result.getPickedCommits()) {
      statuses.add(picked.getStatus());
    }
    return statuses;
  }

  @NotNull
  private static List<String> createdRevisions(@NotNull CherryPickResult result) {
    List<String> revisions = new ArrayList<>();
    for (CherryPickResult.PickedCommit picked : result.getPickedCommits()) {
      if (picked.getCreatedRevision() != null)
        revisions.add(picked.getCreatedRevision());
    }
    return revisions;
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
  private static String fullMessage(@NotNull File bareRepo, @NotNull String revision) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      return rw.parseCommit(ObjectId.fromString(revision)).getFullMessage();
    }
  }

  @NotNull
  private static String authorIdent(@NotNull File bareRepo, @NotNull String revision) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      return format(rw.parseCommit(ObjectId.fromString(revision)).getAuthorIdent());
    }
  }

  @NotNull
  private static String committerIdent(@NotNull File bareRepo, @NotNull String revision) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      return format(rw.parseCommit(ObjectId.fromString(revision)).getCommitterIdent());
    }
  }

  @NotNull
  private static String format(@NotNull PersonIdent ident) {
    return ident.getName() + " <" + ident.getEmailAddress() + ">";
  }

  @Nullable
  private static String fileContent(@NotNull File bareRepo, @NotNull String revision, @NotNull String path) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      RevCommit commit = rw.parseCommit(ObjectId.fromString(revision));
      try (TreeWalk tw = TreeWalk.forPath(r, path, commit.getTree())) {
        if (tw == null) return null;
        return new String(r.open(tw.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  private static boolean isReachable(@NotNull File bareRepo, @NotNull String revision, @NotNull String tip) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build();
         RevWalk rw = new RevWalk(r)) {
      return rw.isMergedInto(rw.parseCommit(ObjectId.fromString(revision)), rw.parseCommit(ObjectId.fromString(tip)));
    }
  }
}
