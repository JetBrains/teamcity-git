package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * Verifies the connectivity and validity of the objects in the database
 */

public interface FsckCommandServer extends GitCommand {
  int fsck(@NotNull String repositoryPath) throws VcsException;
}
