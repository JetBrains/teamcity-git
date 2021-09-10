package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

public interface PushCommand {
  @NotNull
  CommitResult push(@NotNull Repository db, @NotNull GitVcsRoot gitRoot,
                    @NotNull String commit, @NotNull String lastCommit,
                    @NotNull CommitSettings settings) throws VcsException;
}
