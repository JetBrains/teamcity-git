package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.jetbrains.annotations.NotNull;

public class MissingSubmoduleCommitException extends CorruptObjectException {

  public MissingSubmoduleCommitException(@NotNull String mainRepositoryUrl,
                                         @NotNull String mainRepositoryCommit,
                                         @NotNull String submodulePath,
                                         @NotNull String submoduleRepositoryUrl,
                                         @NotNull String submoduleCommit) {
    super("Cannot find the commit " + submoduleCommit +
          " in the repository '" + submoduleRepositoryUrl +
          "' used as a submodule by the repository '" + mainRepositoryUrl +
          "' in the commit " + mainRepositoryCommit +
          " at a path '" + submodulePath + "'");
  }
}
