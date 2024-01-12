

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;

/**
 * Exception is thrown when we cannot fetch some submodule
 *
 * @author dmitry.neverov
 */
public class SubmoduleFetchException extends CorruptObjectException {

  public SubmoduleFetchException(@NotNull String mainRepositoryUrl,
                                 @NotNull String submodulePath,
                                 @NotNull String submoduleUrl,
                                 @NotNull ObjectId mainRepositoryCommit,
                                 @NotNull Throwable cause) {
    super("Cannot fetch the '" + submoduleUrl
          + "' repository used as a submodule at the '" + submodulePath
          + "' path in the '" + mainRepositoryUrl
          + "' repository in the " + mainRepositoryCommit.name() + " commit"
          + ", cause: " + cause.getClass().getName() + ": " + cause.getMessage());
    initCause(cause);
  }

  public SubmoduleFetchException(@NotNull String mainRepositoryUrl,
                                 @NotNull String submodulePath,
                                 @NotNull String submodulePathFromRoot) {
    super(getMessage(mainRepositoryUrl, submodulePath, submodulePathFromRoot));
  }


  private static String getMessage(String repositoryUrl, String submodulePath, String submodulePathFromRoot) {
    if (submodulePath.equals(submodulePathFromRoot)) {
      return String.format("Cannot fetch submodule. Repository URL: '%s', submodule path: '%s'.",
                           repositoryUrl, submodulePath);
    } else {
      return String.format("Cannot fetch submodule. Repository URL: '%s', submodule path: '%s', submodule path from the root: '%s'.",
                           repositoryUrl, submodulePath, submodulePathFromRoot);
    }
  }

}