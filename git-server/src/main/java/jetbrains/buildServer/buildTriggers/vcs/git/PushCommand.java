package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

public interface PushCommand extends GitCommand {
  @NotNull
  CommitResult push(@NotNull Repository db, @NotNull GitVcsRoot gitRoot,
                    @NotNull String ref,
                    @NotNull String commit, @NotNull String lastCommit) throws VcsException;

  /**
   * The same push, which the remote side must reject unless the ref still points to {@code lastCommit} — the zero
   * id means the ref must not exist — whatever {@link ServerPluginConfig#isPushWithLeaseEnabled()} says. For the
   * operations whose contract promises that a ref updated concurrently is never overwritten.
   *
   * <p>The default is for an implementation whose {@link #push} always sends the expected revision with the
   * update; one which can push without that check must override this method.
   */
  @NotNull
  default CommitResult pushWithLease(@NotNull Repository db, @NotNull GitVcsRoot gitRoot,
                                     @NotNull String ref,
                                     @NotNull String commit, @NotNull String lastCommit) throws VcsException {
    return push(db, gitRoot, ref, commit, lastCommit);
  }
}
