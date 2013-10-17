package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.jetbrains.annotations.NotNull;

public class MissingSubmoduleConfigException extends CorruptObjectException {

  public MissingSubmoduleConfigException(@NotNull String mainRepositoryUrl,
                                         @NotNull String mainRepositoryCommit,
                                         @NotNull String submodulePath) {
    super("The repository '" + mainRepositoryUrl +
          "' has a submodule in the commit '" + mainRepositoryCommit +
          "' at a path '" + submodulePath + "', but has no .gitmodules configuration at the root directory");
  }
}
