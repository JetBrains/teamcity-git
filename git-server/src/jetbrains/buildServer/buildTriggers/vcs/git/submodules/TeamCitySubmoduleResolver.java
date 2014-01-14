/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.CommitLoader;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

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

  public TeamCitySubmoduleResolver(@NotNull CommitLoader commitLoader,
                                   @NotNull OperationContext context,
                                   @NotNull Repository db,
                                   @NotNull RevCommit commit) {
    this(commitLoader, context, db, commit, "");
  }


  private TeamCitySubmoduleResolver(@NotNull CommitLoader commitLoader,
                                    @NotNull OperationContext context,
                                    Repository db,
                                    RevCommit commit,
                                    String basePath) {
    super(context.getSupport(), commitLoader, db, commit);
    myContext = context;
    myPathFromRoot = basePath;
  }


  protected Repository resolveRepository(String path, String submoduleUrl) throws IOException, VcsException, URISyntaxException {
    LOG.debug("Resolve repository for URL: " + submoduleUrl);
    final URIish uri = resolveUrl(submoduleUrl);
    Repository r = myContext.getRepositoryFor(uri);
    LOG.debug("Repository dir for submodule " + submoduleUrl + " is " + r.getDirectory().getAbsolutePath());
    return r;
  }

  @Override
  protected void fetch(Repository r, String submodulePath, String submoduleUrl) throws VcsException, URISyntaxException, IOException {
    if (LOG.isDebugEnabled())
      LOG.debug("Fetching submodule " + submoduleUrl + " used at " + submodulePath + " for " + myContext.getGitRoot().debugInfo());
    URIish uri = resolveUrl(submoduleUrl);
    myContext.fetchSubmodule(r, uri, Arrays.asList(new RefSpec("+refs/heads/*:refs/heads/*"), new RefSpec("+refs/tags/*:refs/tags/*")), myContext.getGitRoot().getAuthSettings());
  }

  private boolean isRelative(String url) {
    return url.startsWith(".");
  }

  private URIish resolveUrl(String url) throws URISyntaxException {
    String uri = isRelative(url) ? resolveRelativeUrl(url) : url;
    return new URIish(uri);
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
    return new TeamCitySubmoduleResolver(myCommitLoader, myContext, db, commit, fullPath(path));
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
