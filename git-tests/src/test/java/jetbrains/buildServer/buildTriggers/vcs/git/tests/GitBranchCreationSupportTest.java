package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.Arrays;
import java.util.function.Function;
import jetbrains.buildServer.buildTriggers.vcs.git.GitBranchCreationSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCherryPickSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRepoOperations;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.BranchCreationResult;
import jetbrains.buildServer.vcs.BranchCreationSupport;
import jetbrains.buildServer.vcs.CherryPickOptions;
import jetbrains.buildServer.vcs.CherryPickResult;
import jetbrains.buildServer.vcs.CherryPickSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.fail;

/**
 * See {@link GitCherryPickSupportTest} for the description of the 'merge' repository used here.
 */
@Test
@TestFor(issues = "TW-102761")
public class GitBranchCreationSupportTest extends BaseRemoteRepositoryTest {

  private static final String MASTER = "f727882267df4f8fe0bc58c18559591918aefc54";
  private static final String MASTER_PARENT = "2ffada4169e6bd7961d107f607fcdcc0e6c7749d";
  private static final String TOPIC_1 = "0003f1603abfe7fd784b95faa8ae4803598694a0";
  private static final String TOPIC_2 = "6ffbeea7e607c069bdfeea5ea10d7b139c06ecca";

  private GitVcsSupport myGit;
  private BranchCreationSupport myBranchCreationSupport;
  private CherryPickSupport myCherryPickSupport;
  private VcsRoot myRoot;
  private File myRemote;

  public GitBranchCreationSupportTest() {
    super("merge");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    createSupports(null);
    myRemote = getRemoteRepositoryDir("merge");
    myRoot = vcsRoot().withFetchUrl(myRemote).build();
  }

  /**
   * @param intercept applied to the real git operations when a test needs to interfere with the push
   */
  private void createSupports(@Nullable Function<GitRepoOperations, GitRepoOperations> intercept) throws Exception {
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder builder = gitSupport().withServerPaths(paths);
    myGit = builder.build();
    GitRepoOperations repoOperations = new GitRepoOperationsImpl(builder.getPluginConfig(),
                                                                 builder.getTransportFactory(),
                                                                 r -> null,
                                                                 (a, b, c) -> {},
                                                                 myKnownHostsManager);
    if (intercept != null)
      repoOperations = intercept.apply(repoOperations);
    myBranchCreationSupport = new GitBranchCreationSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(), repoOperations);
    myCherryPickSupport = new GitCherryPickSupport(myGit, builder.getCommitLoader(), builder.getRepositoryManager(),
                                                   repoOperations);
  }


  public void creates_branch_at_the_specified_revision() throws Exception {
    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.CREATED);
    then(result.getBranchName()).isEqualTo("refs/heads/release-1.0");
    then(result.getRevision()).isEqualTo(MASTER);
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isEqualTo(MASTER);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(MASTER);
  }


  public void creates_branch_at_a_revision_which_is_not_a_branch_tip() throws Exception {
    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-0.9", MASTER_PARENT);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.CREATED);
    then(resolveRef(myRemote, "refs/heads/release-0.9")).isEqualTo(MASTER_PARENT);
  }


  public void accepts_short_branch_name() throws Exception {
    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "release-1.0", MASTER);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.CREATED);
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isEqualTo(MASTER);
  }


  public void returns_the_existing_branch_instead_of_creating_it_again() throws Exception {
    myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);

    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.ALREADY_AT_REVISION);
    then(result.getRevision()).isEqualTo(MASTER);
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isEqualTo(MASTER);
  }


  public void does_not_move_the_existing_branch() throws Exception {
    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/master", MASTER_PARENT);

    then(result.getStatus())
      .overridingErrorMessage("the branch exists at another revision, which the caller has to be able to detect")
      .isEqualTo(BranchCreationResult.Status.EXISTS_AT_OTHER_REVISION);
    then(result.getRevision()).isEqualTo(MASTER);
    then(resolveRef(myRemote, "refs/heads/master")).isEqualTo(MASTER);
  }


  public void creates_a_branch_which_was_deleted_remotely_after_it_was_mirrored() throws Exception {
    myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);
    //the mirror keeps the branch until it is pruned, and that stale ref must not block the creation
    deleteRef(myRemote, "refs/heads/release-1.0");

    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER_PARENT);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.CREATED);
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isEqualTo(MASTER_PARENT);
  }


  public void does_not_move_a_branch_created_between_the_check_and_the_push() throws Exception {
    //somebody creates the branch at an ancestor of the requested revision in the window the operation cannot see:
    //a push without a lease would fast-forward it and report the branch as created
    createSupports(ops -> new PushInterceptingRepoOperations(ops)
      .beforePush(() -> setRef(myRemote, "refs/heads/release-1.0", MASTER_PARENT)));

    BranchCreationResult result = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);

    then(result.getStatus()).isEqualTo(BranchCreationResult.Status.EXISTS_AT_OTHER_REVISION);
    then(result.getRevision()).isEqualTo(MASTER_PARENT);
    then(resolveRef(myRemote, "refs/heads/release-1.0"))
      .overridingErrorMessage("the branch created by somebody else must not be moved")
      .isEqualTo(MASTER_PARENT);
  }


  public void rejects_a_name_which_is_not_a_branch_ref() throws Exception {
    //a qualified ref is taken as is, so a tag must not be created by an operation which creates a branch
    try {
      myBranchCreationSupport.createBranch(myRoot, "refs/tags/v1.0", MASTER);
      fail("VcsException is expected for a ref which is not a branch");
    } catch (VcsException e) {
      then(e.getMessage()).contains("refs/tags/v1.0");
    }
    then(resolveRef(myRemote, "refs/tags/v1.0")).isNull();
  }


  public void rejects_a_revision_which_is_not_a_full_one() throws Exception {
    try {
      myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER.substring(0, 8));
      fail("VcsException is expected for a revision which is not a full one");
    } catch (VcsException e) {
      then(e.getMessage()).contains(MASTER.substring(0, 8));
    }
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isNull();
  }


  public void rejects_unknown_revision() throws Exception {
    try {
      myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", ObjectId.zeroId().name());
      fail("VcsException is expected for a revision which doesn't exist");
    } catch (VcsException e) {
      //expected
    }
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isNull();
  }


  public void hotfix_flow_creates_a_release_branch_and_picks_commits_into_it() throws Exception {
    BranchCreationResult branch = myBranchCreationSupport.createBranch(myRoot, "refs/heads/release-1.0", MASTER);
    then(branch.getStatus()).isEqualTo(BranchCreationResult.Status.CREATED);

    CherryPickResult picked = myCherryPickSupport.cherryPick(myRoot, Arrays.asList(TOPIC_1, TOPIC_2),
                                                             "refs/heads/release-1.0", CherryPickOptions.create());

    then(picked.getStatus()).isEqualTo(CherryPickResult.Status.PICKED);
    then(resolveRef(myRemote, "refs/heads/release-1.0")).isEqualTo(picked.getNewBranchRevision());
    then(resolveRef(myRemote, "refs/heads/master"))
      .overridingErrorMessage("the hotfix must not touch the branch it was created from")
      .isEqualTo(MASTER);
  }


  private static void setRef(@NotNull File bareRepo, @NotNull String ref, @NotNull String revision) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build()) {
      RefUpdate update = r.updateRef(GitUtils.expandRef(ref));
      update.setNewObjectId(ObjectId.fromString(revision));
      update.setForceUpdate(true);
      then(update.forceUpdate()).isIn(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD);
    }
  }

  private static void deleteRef(@NotNull File bareRepo, @NotNull String ref) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build()) {
      RefUpdate update = r.updateRef(GitUtils.expandRef(ref));
      update.setForceUpdate(true);
      RefUpdate.Result result = update.delete();
      then(result).isIn(RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD, RefUpdate.Result.NO_CHANGE);
    }
  }

  @Nullable
  private static String resolveRef(@NotNull File bareRepo, @NotNull String ref) throws Exception {
    try (Repository r = new RepositoryBuilder().setBare().setGitDir(bareRepo).build()) {
      Ref reference = r.exactRef(GitUtils.expandRef(ref));
      return reference == null ? null : reference.getObjectId().name();
    }
  }
}
