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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Configures remote repository
 */
public class RemoteRepositoryConfigurator {

  private boolean myExcludeUsernameFromHttpUrls;
  private File myGitDir;


  public void setExcludeUsernameFromHttpUrls(boolean excludeUsernameFromHttpUrls) {
    myExcludeUsernameFromHttpUrls = excludeUsernameFromHttpUrls;
  }


  public void setGitDir(@NotNull File gitDir) {
    myGitDir = gitDir;
  }


  /**
   * Configures and save the remote repository for specified VCS root
   * @param root VCS root of interest
   * @throws VcsException in case of any error
   */
  public void configure(@NotNull GitVcsRoot root) throws VcsException {
    File gitDir = getGitDir();
    Repository repository = null;
    try {
      repository = new RepositoryBuilder().setGitDir(gitDir).build();
      StoredConfig config = repository.getConfig();
      URIish fetchUrl = root.getRepositoryFetchURL();
      String scheme = fetchUrl.getScheme();
      String user = fetchUrl.getUser();
      if (myExcludeUsernameFromHttpUrls && isHttp(scheme) && !StringUtil.isEmpty(user)) {
        URIish fetchUrlNoUser = fetchUrl.setUser(null);
        config.setString("remote", "origin", "url", fetchUrlNoUser.toString());
        config.setString("credential", null, "username", user);
      } else {
        config.setString("remote", "origin", "url", fetchUrl.toString());
        config.unset("credential", null, "username");
      }
      config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
      config.save();
    } catch (Exception e) {
      throw new VcsException("Error while configuring remote repository at " + gitDir, e);
    } finally {
      if (repository != null)
        repository.close();
    }
  }


  private boolean isHttp(@Nullable String scheme) {
    return "http".equals(scheme) || "https".equals(scheme);
  }


  @NotNull
  private File getGitDir() {
    if (myGitDir != null)
      return myGitDir;
    throw new IllegalStateException("Git directory is not specified");
  }
}
