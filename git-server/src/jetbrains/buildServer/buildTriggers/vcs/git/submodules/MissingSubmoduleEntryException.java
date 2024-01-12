

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MissingSubmoduleEntryException extends CorruptObjectException implements SubmoduleException {

  private final String myMainRepositoryUrl;
  private final String myMainRepositoryCommit;
  private final String mySubmodulePath;

  public MissingSubmoduleEntryException(@NotNull String mainRepositoryUrl,
                                        @NotNull String mainRepositoryCommit,
                                        @NotNull String submodulePath,
                                        @NotNull Set<String> branches) {
    super(buildMessage(mainRepositoryUrl, mainRepositoryCommit, submodulePath, branches));
    myMainRepositoryUrl = mainRepositoryUrl;
    myMainRepositoryCommit = mainRepositoryCommit;
    mySubmodulePath = submodulePath;
  }


  public MissingSubmoduleEntryException(@NotNull String mainRepositoryUrl,
                                        @NotNull String mainRepositoryCommit,
                                        @NotNull String submodulePath) {
    this(mainRepositoryUrl, mainRepositoryCommit, submodulePath, Collections.<String>emptySet());
  }


  @NotNull
  public String getMainRepositoryCommit() {
    return myMainRepositoryCommit;
  }


  @NotNull
  public MissingSubmoduleEntryException addBranches(@NotNull Set<String> branches) {
    MissingSubmoduleEntryException result = new MissingSubmoduleEntryException(myMainRepositoryUrl, myMainRepositoryCommit, mySubmodulePath, branches);
    result.setStackTrace(getStackTrace());
    return result;
  }


  @NotNull
  private static String buildMessage(@NotNull String mainRepositoryUrl,
                                     @NotNull String mainRepositoryCommit,
                                     @NotNull String submodulePath,
                                     @NotNull Set<String> branches) {
    StringBuilder result = new StringBuilder();
    result.append("The repository '").append(mainRepositoryUrl)
      .append("' has a submodule in the '").append(mainRepositoryCommit)
      .append("' commit at the '").append(submodulePath)
      .append("' path, but has no entry for this path in .gitmodules configuration");
    SubmoduleExceptionUtil.addAffectedBranches(result, branches);
    return result.toString();
  }
}