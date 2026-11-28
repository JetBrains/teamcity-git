

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsOperationRejectedException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
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
   * @param remoteRefsSupplier the refs of the remote repository with the commits they point to, read only if something has to be fetched
   * @throws VcsException if unable to find any of the tip revisions after fetching twice
   * @throws VcsOperationRejectedException if unable to perform the operation straight away (retry later possible)
   */
  void loadCommits(@NotNull OperationContext context,
                   @NotNull URIish fetchURI,
                   @NotNull Collection<RefCommit> revisions,
                   @NotNull Supplier<Map<String, Ref>> remoteRefsSupplier) throws IOException, VcsException;

  /**
   * The same as {@link #loadCommits(OperationContext, URIish, Collection, Supplier)}, but waits for the write lock of the local clone
   * not longer than the specified time rather than {@link ServerPluginConfig#repositoryWriteLockTimeout()}
   * @param writeLockTimeoutMillis how long to wait for the write lock, 0 or less means waiting as long as it takes
   * @param failFastOnMovedTips whether to fail with the recoverable error at once, rather than after fetching all refs, when the refs of the tips missing after
   *                            the selective fetch point to other commits in the remote repository, see {@link ServerPluginConfig#failFastOnMovedTips()};
   *                            only for a caller which takes a new state after the error
   * @throws jetbrains.buildServer.vcs.VcsOperationRejectedException if the write lock is not acquired in time
   */
  void loadCommits(@NotNull OperationContext context,
                   @NotNull URIish fetchURI,
                   @NotNull Collection<RefCommit> revisions,
                   @NotNull Supplier<Map<String, Ref>> remoteRefsSupplier,
                   long writeLockTimeoutMillis,
                   boolean failFastOnMovedTips) throws IOException, VcsException;

  /**
   * Checks the local clone without taking its write lock.
   * @return true if {@link #loadCommits(OperationContext, URIish, Collection, Supplier, long, boolean)} would fetch some of the revisions
   */
  boolean isFetchNeeded(@NotNull OperationContext context, @NotNull Collection<RefCommit> revisions) throws VcsException;

  @NotNull
  RevCommit getCommit(@NotNull Repository repository, @NotNull ObjectId commitId) throws IOException;

  @Nullable
  RevCommit findCommit(@NotNull Repository r, @NotNull String sha);

}