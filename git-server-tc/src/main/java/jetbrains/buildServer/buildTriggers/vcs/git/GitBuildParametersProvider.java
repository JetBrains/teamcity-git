

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.RepositoryVersion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;

/**
 * Adds parameters to the build which can be used during agent-side checkout
 */
public class GitBuildParametersProvider extends AbstractBuildParametersProvider {


  @NotNull
  @Override
  public Map<String, String> getParameters(@NotNull SBuild build, boolean emulationMode) {
    if (TeamCityProperties.getBoolean(Constants.USE_DEPRECATED_GIT_BRANCH_PARAMETERS_INTERNAL_PROP)) {
      Map<String, String> params = new HashMap<String, String>();
      if (emulationMode)
        return params;
      for (BuildRevision revision : build.getRevisions()) {
        RepositoryVersion repositoryVersion = revision.getRepositoryVersion();
        VcsRootInstance root = revision.getRoot();
        if (!Constants.VCS_NAME.equals(root.getVcsName()))
          continue;
        String vcsBranch = repositoryVersion.getVcsBranch();
        if (vcsBranch != null)
          params.put(GitUtils.getGitRootBranchParamName(root), vcsBranch);
      }
      return params;
    } else {
      return Collections.emptyMap();
    }
  }

  @NotNull
  @Override
  public String getPrefix() {
    return Constants.GIT_ROOT_BUILD_BRANCH_PREFIX;
  }
}