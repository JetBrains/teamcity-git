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
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;

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
  /**
   * The settings object
   */
  private final Settings myBaseRepositorySettings;
  private final OperationContext myContext;

  /**
   * The resolver constructor
   *
   * @param context current operation context
   * @param vcs                   the Git vcs service
   * @param commit                the commit this resolves handles
   */
  public TeamCitySubmoduleResolver(OperationContext context, GitVcsSupport vcs, RevCommit commit, Repository db) throws VcsException {
    this(context, vcs, context.getSettings(), "", commit, db);
  }

  /**
   * The resolver constructor
   *
   * @param context current operation context
   * @param vcs                   the Git vcs service
   * @param settings              the settings object
   * @param basePath              the base path
   * @param commit                the commit this resolves handles
   */
  private TeamCitySubmoduleResolver(OperationContext context,
                                    GitVcsSupport vcs,
                                    Settings settings,
                                    String basePath,
                                    RevCommit commit,
                                    Repository db) {
    super(vcs, db, commit);
    myContext = context;
    myPathFromRoot = basePath;
    myBaseRepositorySettings = settings;
  }

  protected Repository resolveRepository(String path, String url) throws IOException, VcsAuthenticationException {
    try {
      if (isRelative(url)) {
        url = resolveRelativeUrl(url);
      }
      String dir = myBaseRepositorySettings.getPathForUrl(url).getPath();
      if (myContext.getRepositories().containsKey(dir)) {
        return myContext.getRepositories().get(dir);
      }
      final URIish uri = new URIish(url);
      final Repository r = myContext.getRepository(new File(dir), uri);

      if (LOG.isDebugEnabled()) {
        LOG.debug("Fetching submodule " + path + " data for " + myBaseRepositorySettings.debugInfo());
      }
      myGitSupport.fetch(r, myBaseRepositorySettings.getAuthSettings(), uri, new RefSpec("+refs/heads/*:refs/heads/*"));
      return r;
    } catch (VcsAuthenticationException ae) {
      throw ae;
    } catch (VcsException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause();
      }
      final IOException ex = new IOException(e.getMessage());
      ex.initCause(e);
      throw ex;
    } catch (URISyntaxException e) {
      final IOException ex = new IOException(e.getMessage());
      ex.initCause(e);
      throw ex;
    }
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
      //ignore
    }
    if (db != null) {
      return new TeamCitySubmoduleResolver(myContext, myGitSupport, myBaseRepositorySettings, fullPath(path), commit, db);
    } else {
      return new TeamCitySubmoduleResolver(myContext, myGitSupport, myBaseRepositorySettings, fullPath(path), commit, getRepository());
    }
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
