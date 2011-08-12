/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * The resolver for submodules that uses TeamCity repository mapping.
 */
public class TeamCitySubmoduleResolver extends SubmoduleResolver {

  private static Logger LOG = Logger.getInstance(TeamCitySubmoduleResolver.class.getName());
  /**
   * Path from the root of the first repository.
   * For root repository = "".
   * For submodule repository = path of submodule.
   * For sub-submodules = path of submodule/path of sub-submodule in submodule repository.
   */
  private final String myPathFromRoot;
  private final OperationContext myContext;
  private final MirrorManager myMirrorManager;

  public TeamCitySubmoduleResolver(@NotNull final OperationContext context,
                                   @NotNull final MirrorManager mirrorManager,
                                   Repository db,
                                   RevCommit commit) {
    this(context, mirrorManager, db, commit, "");
  }


  private TeamCitySubmoduleResolver(@NotNull final OperationContext context,
                                    @NotNull final MirrorManager mirrorManager,
                                    Repository db,
                                    RevCommit commit,
                                    String basePath) {
    super(context.getSupport(), db, commit);
    myMirrorManager = mirrorManager;
    myContext = context;
    myPathFromRoot = basePath;
  }


  protected Repository resolveRepository(String path, String url) throws IOException, VcsException, URISyntaxException {
    if (LOG.isDebugEnabled())
      LOG.debug("Resolve repository for URL: " + url);

    if (isRelative(url)) {
      url = resolveRelativeUrl(url);
    }
    File repositoryDir = myMirrorManager.getMirrorDir(url);

    if (LOG.isDebugEnabled())
      LOG.debug("Cache dir for repository '" + url + "' is '" + repositoryDir.getAbsolutePath() + "'");

    Repository result = myContext.getRepositoryFor(repositoryDir);
    if (result == null) {
      final URIish uri = new URIish(url);
      result = myContext.getRepository(repositoryDir, uri);
      if (LOG.isDebugEnabled())
        LOG.debug("Fetching submodule " + url + " used at " + path + " for " + myContext.getSettings().debugInfo());
      myGitSupport.fetch(result, uri, new RefSpec("+refs/heads/*:refs/heads/*"), myContext.getSettings().getAuthSettings());
    }
    checkRepositoryCanBeUsedForUrl(result, url);
    return result;
  }

  private void checkRepositoryCanBeUsedForUrl(Repository result, String url) {
    String teamcityRemote = result.getConfig().getString("teamcity", null, "remote");
    if (teamcityRemote != null && !url.equals(teamcityRemote))
      LOG.warn("Directory '" + result.getDirectory().getAbsolutePath() + "' is used for 2 different repositories: '" + url + "' and '" + teamcityRemote + "'");
  }

  private boolean isRelative(String url) {
    return url.startsWith(".");
  }

  private String resolveRelativeUrl(String relativeUrl) throws URISyntaxException {
    String baseUrl = getRepository().getConfig().getString("teamcity", null, "remote");
    URIish u = new URIish(baseUrl);
    String newPath = u.getPath();
    if (newPath.length() == 0) {
      newPath = relativeUrl;
    } else {
      newPath = GitUtils.normalizePath(newPath + '/' + relativeUrl);
    }
    return u.setPath(newPath).toPrivateString();
  }

  public SubmoduleResolver getSubResolver(RevCommit commit, String path) {
    Repository db = null;
    try {
      db = resolveRepository(path, getSubmoduleUrl(path));
    } catch (Exception e) {
      //exception means path does not contain submodule, use current repository
      db = getRepository();
    }
    return new TeamCitySubmoduleResolver(myContext, myMirrorManager, db, commit, fullPath(path));
  }

  /**
   * Get full path using from local path
   *
   * @param path the path to examine
   * @return the full including the base path
   */
  private String fullPath(String path) {
    return myPathFromRoot.length() == 0 ? path : myPathFromRoot + "/" + path;
  }
}
