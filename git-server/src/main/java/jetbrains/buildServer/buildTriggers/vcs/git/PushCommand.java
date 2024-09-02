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
}
