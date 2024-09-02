

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface SubmoduleManager {

  /**
   * Persists the provided submodules into "teamcity.submodules" file inside the provided repository mirror
   *
   * @param repositoryUrl parent repository
   * @param submodules set of submodule URLs
   */
  void persistSubmodules(@NotNull String repositoryUrl, @NotNull Collection<String> submodules);

  /**
   * Returns submodules specified inside "teamcity.submodules" file inside the provided repository mirror
   * @param repositoryUrl parent repository
   * @return set of submodule URLs
   */
  @NotNull
  Collection<String> getSubmodules(@NotNull String repositoryUrl);
}