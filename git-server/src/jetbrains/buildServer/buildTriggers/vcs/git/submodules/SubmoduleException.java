

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface SubmoduleException {

  @NotNull
  String getMainRepositoryCommit();

  @NotNull
  Exception addBranches(@NotNull Set<String> branches);

}