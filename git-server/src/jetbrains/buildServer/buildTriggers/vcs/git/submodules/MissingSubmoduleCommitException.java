

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class MissingSubmoduleCommitException extends CorruptObjectException implements SubmoduleException {

  private final String myMainRepositoryUrl;
  private final String myMainRepositoryCommit;
  private final String mySubmodulePath;
  private final String mySubmoduleRepositoryUrl;
  private final String mySubmoduleCommit;

  public MissingSubmoduleCommitException(@NotNull String mainRepositoryUrl,
                                         @NotNull String mainRepositoryCommit,
                                         @NotNull String submodulePath,
                                         @NotNull String submoduleRepositoryUrl,
                                         @NotNull String submoduleCommit,
                                         @NotNull Set<String> branch) {
    super(buildMessage(mainRepositoryUrl, mainRepositoryCommit, submodulePath, submoduleRepositoryUrl, submoduleCommit, branch));
    myMainRepositoryUrl = mainRepositoryUrl;
    myMainRepositoryCommit = mainRepositoryCommit;
    mySubmodulePath = submodulePath;
    mySubmoduleRepositoryUrl = submoduleRepositoryUrl;
    mySubmoduleCommit = submoduleCommit;
  }


  public MissingSubmoduleCommitException(@NotNull String mainRepositoryUrl,
                                         @NotNull String mainRepositoryCommit,
                                         @NotNull String submodulePath,
                                         @NotNull String submoduleRepositoryUrl,
                                         @NotNull String submoduleCommit) {
    this(mainRepositoryUrl, mainRepositoryCommit, submodulePath, submoduleRepositoryUrl, submoduleCommit, Collections.<String>emptySet());
  }


  @NotNull
  public String getMainRepositoryCommit() {
    return myMainRepositoryCommit;
  }


  @NotNull
  public MissingSubmoduleCommitException addBranches(@NotNull Set<String> branches) {
    MissingSubmoduleCommitException result = new MissingSubmoduleCommitException(myMainRepositoryUrl, myMainRepositoryCommit, mySubmodulePath,
                                                                                 mySubmoduleRepositoryUrl, mySubmoduleCommit, branches);
    result.setStackTrace(getStackTrace());
    return result;
  }


  @NotNull
  private static String buildMessage(@NotNull String mainRepositoryUrl,
                                     @NotNull String mainRepositoryCommit,
                                     @NotNull String submodulePath,
                                     @NotNull String submoduleRepositoryUrl,
                                     @NotNull String submoduleCommit,
                                     @NotNull Set<String> branches) {
    StringBuilder result = new StringBuilder();
    result.append("Cannot find the '").append(submoduleCommit).append("' commit in the '")
      .append(submoduleRepositoryUrl).append("' repository used as a submodule by the '")
      .append(mainRepositoryUrl).append("' repository in the '")
      .append(mainRepositoryCommit).append("' commit at the '")
      .append(submodulePath).append("' path");
    SubmoduleExceptionUtil.addAffectedBranches(result, branches);
    return result.toString();
  }

}