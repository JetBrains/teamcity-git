package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.CommitLoaderImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitLoaderRefTipReachabilityTest extends BaseTestCase {

  private static final String BRANCH = "refs/heads/branch";
  private static final String MISSING_REVISION = "0123456789abcdef0123456789abcdef01234567";
  private static final int LIMIT = 50;

  private final TempFiles myTempFiles = new TempFiles();
  private Git myGit;
  private Repository myRepository;
  private RevWalk myWalk;
  private final List<RevCommit> myHistory = new ArrayList<>();

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File dir = myTempFiles.createTempDir();
    myGit = Git.init().setDirectory(dir).call();
    myRepository = myGit.getRepository();
    myWalk = new RevWalk(myRepository);
    myHistory.clear();
    for (int i = 0; i < 5; i++) {
      myHistory.add(commit("commit " + i));
    }
    setLocalRef(myHistory.get(4));
  }

  @AfterMethod(alwaysRun = true)
  @Override
  protected void tearDown() throws Exception {
    myWalk.close();
    myGit.close();
    myTempFiles.cleanup();
    super.tearDown();
  }

  public void local_ref_pointing_to_revision() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(1), myHistory.get(4), LIMIT)).isTrue();
  }

  public void local_ref_pointing_to_revision_without_old_state() throws Exception {
    then(isLocalRefUpToDate(null, myHistory.get(4), LIMIT)).isTrue();
  }

  public void local_ref_pointing_to_revision_when_reachability_check_disabled() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(1), myHistory.get(4), 0)).isTrue();
  }

  public void local_ref_ahead_of_branch_moved_forward() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(2), LIMIT)).isTrue();
  }

  public void local_ref_ahead_of_unchanged_branch() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(2), myHistory.get(2), LIMIT)).isTrue();
  }

  public void local_ref_ahead_by_exactly_the_limit() throws Exception {
    // the local tip itself is the 1st visited commit, the revision 3 commits behind it is the 4th one
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(1), 4)).isTrue();
  }

  public void local_ref_ahead_further_than_the_limit() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(1), 3)).isFalse();
  }

  public void local_ref_ahead_when_reachability_check_disabled() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(3), 0)).isFalse();
  }

  public void local_ref_ahead_without_old_state() throws Exception {
    then(isLocalRefUpToDate(null, myHistory.get(3), LIMIT)).isFalse();
  }

  public void local_ref_ahead_with_old_revision_missing_from_clone() throws Exception {
    then(CommitLoaderImpl.isLocalRefUpToDate(myWalk, myRepository.exactRef(BRANCH), MISSING_REVISION, myHistory.get(3).name(), LIMIT)).isFalse();
  }

  public void branch_moved_back_by_force_push() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(4), myHistory.get(2), LIMIT)).isFalse();
  }

  public void branch_moved_back_while_local_ref_is_ahead_of_old_state() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(3), myHistory.get(1), LIMIT)).isFalse();
  }

  public void branch_force_pushed_from_another_line_of_history() throws Exception {
    resetHead(myHistory.get(2));
    RevCommit sideCommit = commit("side");
    // the old state pointed to a commit which is not an ancestor of the new one
    then(isLocalRefUpToDate(sideCommit, myHistory.get(3), LIMIT)).isFalse();
  }

  public void branch_moved_forward_with_commit_dates_going_back() throws Exception {
    resetHead(myHistory.get(4));
    long timeMillis = myHistory.get(0).getCommitTime() * 1000L;
    RevCommit c5 = commit("commit 5", timeMillis - 2000);
    RevCommit c6 = commit("commit 6", timeMillis - 4000);
    RevCommit c7 = commit("commit 7", timeMillis - 6000);
    setLocalRef(c7);

    then(isLocalRefUpToDate(myHistory.get(3), c6, LIMIT)).isTrue();
    then(isLocalRefUpToDate(c6, c5, LIMIT)).isFalse();
  }

  public void local_ref_behind_revision() throws Exception {
    setLocalRef(myHistory.get(2));
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(4), LIMIT)).isFalse();
  }

  public void revision_on_another_branch() throws Exception {
    resetHead(myHistory.get(2));
    RevCommit sideCommit = commit("side");
    then(isLocalRefUpToDate(myHistory.get(2), sideCommit, LIMIT)).isFalse();
  }

  public void revision_reachable_through_second_parent_of_merge() throws Exception {
    resetHead(myHistory.get(2));
    RevCommit sideCommit = commit("side");
    resetHead(myHistory.get(4));
    myGit.merge().include(sideCommit).setMessage("merge").call();
    setLocalRef(myRepository.resolve(Constants.HEAD));

    then(isLocalRefUpToDate(myHistory.get(2), sideCommit, LIMIT)).isTrue();
  }

  public void revision_missing_from_clone() throws Exception {
    then(CommitLoaderImpl.isLocalRefUpToDate(myWalk, myRepository.exactRef(BRANCH), myHistory.get(0).name(), MISSING_REVISION, LIMIT)).isFalse();
  }

  public void no_local_ref() throws Exception {
    then(CommitLoaderImpl.isLocalRefUpToDate(myWalk, null, myHistory.get(0).name(), myHistory.get(4).name(), LIMIT)).isFalse();
  }

  public void walk_stays_usable_after_the_check() throws Exception {
    then(isLocalRefUpToDate(myHistory.get(0), myHistory.get(1), LIMIT)).isTrue();
    then(isLocalRefUpToDate(myHistory.get(4), myHistory.get(1), LIMIT)).isFalse();
    then(myWalk.parseCommit(myHistory.get(2)).getFullMessage()).isEqualTo("commit 2");
  }

  private boolean isLocalRefUpToDate(@Nullable RevCommit fromRevision, @NotNull RevCommit revision, int maxCommitsToVisit) throws Exception {
    return CommitLoaderImpl.isLocalRefUpToDate(myWalk, myRepository.exactRef(BRANCH), fromRevision == null ? null : fromRevision.name(),
                                               revision.name(), maxCommitsToVisit);
  }

  @NotNull
  private RevCommit commit(@NotNull String message) throws Exception {
    return commit(message, System.currentTimeMillis());
  }

  @NotNull
  private RevCommit commit(@NotNull String message, long timeMillis) throws Exception {
    PersonIdent person = new PersonIdent("user", "user@example.com", timeMillis, 0);
    return myGit.commit().setMessage(message).setAllowEmpty(true).setAuthor(person).setCommitter(person).call();
  }

  private void resetHead(@NotNull AnyObjectId commit) throws Exception {
    myGit.reset().setMode(ResetCommand.ResetType.HARD).setRef(commit.name()).call();
  }

  private void setLocalRef(@NotNull AnyObjectId commit) throws Exception {
    RefUpdate update = myRepository.updateRef(BRANCH);
    update.setNewObjectId(commit);
    update.setForceUpdate(true);
    update.update();
  }
}
