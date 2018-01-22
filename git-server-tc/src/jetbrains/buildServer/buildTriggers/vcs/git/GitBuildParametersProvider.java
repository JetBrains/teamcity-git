/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.RepositoryVersion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds parameters to the build which can be used during agent-side checkout
 */
public class GitBuildParametersProvider extends AbstractBuildParametersProvider {
  @NotNull
  @Override
  public Map<String, String> getParameters(@NotNull SBuild build, boolean emulationMode) {
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
  }
}
