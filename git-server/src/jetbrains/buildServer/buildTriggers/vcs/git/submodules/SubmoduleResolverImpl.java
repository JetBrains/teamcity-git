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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * The resolver for submodules
 */
public class SubmoduleResolverImpl implements SubmoduleResolver {

  private static Logger LOG = Logger.getInstance(SubmoduleResolverImpl.class.getName());
  /**
   * Path from the root of the first repository.
   * For root repository = "".
   * For submodule repository = path of submodule.
   * For sub-submodules = path of submodule/path of sub-submodule in submodule repository.
   */
  protected final String myPathFromRoot;
  protected final OperationContext myContext;

  private final RevCommit myCommit;
  private final Repository myDb;
  protected final CommitLoader myCommitLoader;
  private SubmodulesConfig myConfig;

  public SubmoduleResolverImpl(@NotNull OperationContext context,
                               @NotNull CommitLoader commitLoader,
                               @NotNull Repository db,
                               @NotNull RevCommit commit,
                               @NotNull String pathFromRoot) {
    myCommitLoader = commitLoader;
    myDb = db;
    myCommit = commit;
    myContext = context;
    myPathFromRoot = pathFromRoot;
  }

  /**
   * Resolve the commit for submodule
   *
   * @param parentRepositoryUrl url of the parent repository
   * @param path   the within repository path
   * @param commit the commit identifier
   * @return the the resoled commit in other repository
   * @throws VcsAuthenticationException if there are authentication problems
   * @throws URISyntaxException if there are errors in submodule repository URI
   */
  @NotNull
  public RevCommit getSubmoduleCommit(@NotNull String parentRepositoryUrl,
                                      @NotNull String path,
                                      @NotNull ObjectId commit) throws CorruptObjectException, VcsException, URISyntaxException {
    ensureConfigLoaded();
    if (myConfig == null)
      throw new MissingSubmoduleConfigException(parentRepositoryUrl, myCommit.name(), path);

    final Submodule submodule = myConfig.findSubmodule(path);
    if (submodule == null)
      throw new MissingSubmoduleEntryException(parentRepositoryUrl, myCommit.name(), path);

    URIish submoduleUri = resolveSubmoduleUrl(submodule.getUrl());
    File repositoryDir = myContext.getRepositoryDir(submoduleUri);
    try {
      return myContext.getRepositoryManager().runWithDisabledRemove(repositoryDir, () -> {
        try {
          Repository r = resolveRepository(submodule.getUrl());
          String submoduleUrl = myContext.getConfig(r).getString("teamcity", null, "remote");

          if (!isCommitExist(r, commit)) {
            try {
              fetch(r, path, submodule.getUrl());
            } catch (Exception e) {
              throw new SubmoduleFetchException(parentRepositoryUrl, path, submoduleUrl, myCommit, e);
            }
          }
          try {
            return myCommitLoader.getCommit(r, commit);
          } catch (Exception e) {
            throw new MissingSubmoduleCommitException(parentRepositoryUrl, myCommit.name(), path, submodule.getUrl(), commit.name());
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof CorruptObjectException) {
        throw (CorruptObjectException) cause;
      }
      if (cause instanceof VcsException) {
        throw (VcsException) cause;
      }
      if (cause instanceof URISyntaxException) {
        throw (URISyntaxException) cause;
      }
      throw new VcsException(e);
    }
  }

  private boolean isCommitExist(final Repository r, final ObjectId commit) {
    RevWalk walk = new RevWalk(r);
    try {
      walk.parseCommit(commit);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public URIish resolveSubmoduleUrl(@NotNull String url) throws URISyntaxException {
    return new URIish(resolveSubmoduleUrl(myContext.getPluginConfig(), myContext.getConfig(getRepository()), url));
  }

  private static boolean isRelative(@NotNull String url) {
    return url.startsWith(".");
  }

  @NotNull
  public static String resolveSubmoduleUrl(@NotNull ServerPluginConfig pluginConfig, @NotNull final Repository repository, @NotNull final String relativeUrl) throws URISyntaxException {
    return resolveSubmoduleUrl(pluginConfig, repository.getConfig(), relativeUrl);
  }


  @NotNull
  private static String resolveSubmoduleUrl(@NotNull ServerPluginConfig pluginConfig, @NotNull StoredConfig mainRepoConfig, @NotNull String submoduleUrl) throws URISyntaxException {
    String mainRepoUrl = mainRepoConfig.getString("teamcity", null, "remote");
    URIish mainRepoUri = new URIish(mainRepoUrl);
    if (!isRelative(submoduleUrl)) {
      String user = mainRepoUri.getUser();
      URIish submoduleUri = new URIish(submoduleUrl);
      if (StringUtil.isNotEmpty(user) && pluginConfig.shouldSetSubmoduleUserInAbsoluteUrls() && AuthSettings.requiresCredentials(submoduleUri)) {
        return submoduleUri.setUser(user).toASCIIString();
      } else {
        return submoduleUrl;
      }
    }

    String newPath = mainRepoUri.getPath();
    if (newPath.length() == 0) {
      newPath = submoduleUrl;
    } else {
      newPath = GitUtils.normalizePath(newPath + '/' + submoduleUrl);
    }
    return mainRepoUri.setPath(newPath).toPrivateString();
  }

  /**
   * Check if the specified directory is a submodule prefix
   *
   * @param path the path to check
   * @return true if the path contains submodules
   */
  public boolean containsSubmodule(String path) {
    ensureConfigLoaded();
    return myConfig != null && myConfig.isSubmodulePrefix(path);
  }

  /**
   * @return the current repository
   */
  public Repository getRepository() {
    return myDb;
  }

  /**
   * Get submodule url by it's path in current repository
   *
   * @param submodulePath path of submodule in current repository
   * @return submodule repository url or null if no submodules is registered for specified path
   */
  public String getSubmoduleUrl(String submodulePath) {
    ensureConfigLoaded();
    if (myConfig != null) {
      Submodule submodule = myConfig.findSubmodule(submodulePath);
      return submodule != null ? submodule.getUrl() : null;
    } else {
      return null;
    }
  }

  /**
   * Ensure that submodule configuration has been loaded.
   */
  private void ensureConfigLoaded() {
    if (myConfig == null) {
      try {
        myConfig = new SubmodulesConfig(myContext.getConfig(myDb), new BlobBasedConfig(null, myDb, myCommit, ".gitmodules"));
      } catch (FileNotFoundException e) {
        // do nothing
      } catch (Exception e) {
        LOG.error("Unable to load or parse submodule configuration at: " + myCommit.getId().name(), e);
      }
    }
  }

  public Repository resolveRepository(@NotNull String submoduleUrl) throws VcsException, URISyntaxException {
    LOG.debug("Resolve repository for URL: " + submoduleUrl);
    final URIish uri = resolveSubmoduleUrl(submoduleUrl);
    Repository r = myContext.getRepositoryFor(uri);
    LOG.debug("Repository dir for submodule " + submoduleUrl + " is " + r.getDirectory().getAbsolutePath());
    return r;
  }

  public void fetch(Repository r, String submodulePath, String submoduleUrl) throws VcsException, URISyntaxException, IOException {
    if (LOG.isDebugEnabled())
      LOG.debug("Fetching submodule " + submoduleUrl + " used at " + submodulePath + " for " + myContext.getGitRoot().debugInfo());
    URIish uri = resolveSubmoduleUrl(submoduleUrl);
    myContext.fetchSubmodule(r, uri, Arrays.asList(new RefSpec("+refs/*:refs/*")), myContext.getGitRoot().getAuthSettings());
  }

  public SubmoduleResolverImpl getSubResolver(RevCommit commit, String path) {
    Repository db = null;
    try {
      db = resolveRepository(getSubmoduleUrl(path));
    } catch (Exception e) {
      //exception means path does not contain submodule, use current repository
      db = getRepository();
    }
    return new SubmoduleResolverImpl(myContext, myCommitLoader, db, commit, fullPath(path));
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
