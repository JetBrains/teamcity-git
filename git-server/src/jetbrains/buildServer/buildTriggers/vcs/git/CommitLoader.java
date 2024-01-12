

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsOperationRejectedException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates logic for loading, fetching and finding commits in repository
 */
public interface CommitLoader {

  @NotNull
  RevCommit loadCommit(@NotNull OperationContext context,
                       @NotNull GitVcsRoot root,
                       @NotNull String revision) throws VcsException, IOException;

  void fetch(@NotNull Repository db,
             @NotNull URIish fetchURI,
             @NotNull FetchSettings settings) throws IOException, VcsException;

  /**
   * Performs fetch only if any of the specified revisions is not in the mirror.
   * First fetches only corresponding branches and, only if there are still any tip revisions missing,
   * fetches all remote refs including or excluding tags depending on VCS root settings and on fetch command implementation.
   * @throws VcsException if unable to find any of the tip revisions after fetching twice
   * @throws VcsOperationRejectedException if unable to perform the operation straight away (retry later possible)
   */
  void loadCommits(@NotNull OperationContext context,
                   @NotNull URIish fetchURI,
                   @NotNull Collection<RefCommit> revisions,
                   @NotNull Set<String> remoteRefs) throws IOException, VcsException;

  @NotNull
  RevCommit getCommit(@NotNull Repository repository, @NotNull ObjectId commitId) throws IOException;

  @Nullable
  RevCommit findCommit(@NotNull Repository r, @NotNull String sha);

  interface RefCommit {
    @NotNull String getRef();
    @NotNull String getCommit();
    boolean isRefTip();
  }
}