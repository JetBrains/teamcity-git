/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Implementation of {@link RemoteRepositoryUrlInvestigator}.
 * The same as RemoteRepositoryUrlInvestigatorImpl in the agent module.
 * The class are not in the common module to make the module do not depends on JGit library.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class RemoteRepositoryUrlInvestigatorImpl implements RemoteRepositoryUrlInvestigator {

  private final static Logger LOG = Logger.getInstance(RemoteRepositoryUrlInvestigatorImpl.class.getName());

  @Override
  @Nullable
  public String getRemoteRepositoryUrl(@NotNull final File repositoryDir) {
    try {
      Repository r = new RepositoryBuilder().setBare().setGitDir(repositoryDir).build();
      StoredConfig config = r.getConfig();
      String teamcityRemote = config.getString("teamcity", null, "remote");
      if (teamcityRemote != null)
        return teamcityRemote;
      return config.getString("remote", "origin", "url");
    } catch (Exception e) {
      LOG.warn("Error while trying to get remote repository url at " + repositoryDir.getAbsolutePath(), e);
      return null;
    }
  }
}
