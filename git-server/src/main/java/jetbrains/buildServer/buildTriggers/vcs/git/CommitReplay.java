package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replays the changes introduced by a single commit on top of another commit, in memory.
 * Used by the rebase mode of {@link GitMergeSupport} and by {@link GitCherryPickSupport}.
 */
class CommitReplay {

  private CommitReplay() {
  }

  /**
   * Applies the changes between {@code base} and {@code original} to the {@code onto} commit.
   *
   * <p>Note that the replayed commit is passed to the merger as 'ours', which is the opposite of what
   * git cherry-pick does. The difference is only visible in conflict markers, which are never produced here,
   * and the order is kept as is because the rebase mode has been using it since it was introduced.
   *
   * @param original commit whose changes should be replayed
   * @param onto commit to apply the changes to
   * @param base commit to compute the changes of {@code original} against, usually its parent
   */
  @NotNull
  static Result replay(@NotNull Repository db,
                       @NotNull RevCommit original,
                       @NotNull RevCommit onto,
                       @NotNull RevCommit base) throws IOException {
    ResolveMerger merger = (ResolveMerger)MergeStrategy.RECURSIVE.newMerger(db, true);
    merger.setBase(base);
    merger.merge(original, onto);

    ObjectId resultTreeId = merger.getResultTreeId();
    if (resultTreeId == null) {
      List<String> conflicts = new ArrayList<String>(merger.getUnmergedPaths());
      Collections.sort(conflicts);
      return new Result(null, conflicts);
    }
    return new Result(resultTreeId, Collections.<String>emptyList());
  }

  static class Result {
    @Nullable private final ObjectId myTreeId;
    @NotNull private final List<String> myConflicts;

    private Result(@Nullable ObjectId treeId, @NotNull List<String> conflicts) {
      myTreeId = treeId;
      myConflicts = conflicts;
    }

    /**
     * @return tree of the replayed commit, null if the changes could not be applied
     */
    @Nullable
    ObjectId getTreeId() {
      return myTreeId;
    }

    /**
     * @return sorted paths which could not be merged, empty if the changes were applied
     */
    @NotNull
    List<String> getConflicts() {
      return myConflicts;
    }

    boolean isConflicted() {
      return myTreeId == null;
    }
  }
}
