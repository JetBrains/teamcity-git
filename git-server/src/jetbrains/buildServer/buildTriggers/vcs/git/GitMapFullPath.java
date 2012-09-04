/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
* @author kir
*/
public class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());
  private final RevisionsCache myCache;
  private GitVcsSupport myGit;


  public GitMapFullPath(@NotNull ServerPluginConfig config) {
    myCache = new RevisionsCache(config.getMapFullPathRevisionCacheSize());
  }


  public void setGitVcs(@NotNull GitVcsSupport git) {
    myGit = git;
  }


  public Collection<String> mapFullPath(@NotNull OperationContext context, @NotNull VcsRootEntry rootEntry, @NotNull String path) throws VcsException {
    GitVcsRoot root = context.getGitRoot();
    LOG.debug("MapFullPath root: " + LogUtil.describe(root) + ", path " + path);
    FullPath fullPath = new FullPath(path);
    if (!fullPath.isValid()) {
      LOG.warn("Invalid path: " + path);
      return Collections.emptySet();
    }

    if (fullPath.containsRevision() && repositoryContainsRevision(context, rootEntry, fullPath.getRevision()))
      return fullPath.getMappedPaths();

    if (!fullPath.containsRevision() && urlsMatch(root, fullPath))
      return fullPath.getMappedPaths();

    return Collections.emptySet();
  }


  private boolean repositoryContainsRevision(@NotNull OperationContext context, @NotNull VcsRootEntry rootEntry, @NotNull String revision) throws VcsException {
    GitVcsRoot root = context.getGitRoot();
    RepositoryRevisionCache repositoryCache = myCache.getRepositoryCache(root);
    Boolean result = repositoryCache.hasRevision(revision);
    if (result != null) {
      LOG.debug("RevisionCache hit: root " + LogUtil.describe(rootEntry.getVcsRoot()) + (result ? "contains " : "doesn't contain ") + "revision " + revision);
      return result;
    } else {
      LOG.debug("RevisionCache miss: root " + LogUtil.describe(rootEntry.getVcsRoot()) + ", revision " + revision + ", lookup commit in repository");
      result = findCommit(context, revision) != null;
      LOG.debug("Root " + LogUtil.describe(rootEntry.getVcsRoot()) + ", revision " + revision + (result ? " wasn't found " : " was found ") + ", cache the result");
      repositoryCache.saveRevision(revision, result);
      return result;
    }
  }


  /**
   * @return revCommit or null if repository has not such commit
   */
  @Nullable
  private RevCommit findCommit(@NotNull OperationContext context, @NotNull String commit) throws VcsException {
    final Repository repository = context.getRepository();
    try {
      return myGit.getCommit(repository, commit);
    } catch (IOException e) {
      return null;
    }
  }


  private boolean urlsMatch(@NotNull GitVcsRoot root, @NotNull FullPath fullPath) {
    String url = removeBranch(fullPath.getRepositoryUrl());

    final URIish uri;
    try {
      uri = new URIish(url);
    } catch (final URISyntaxException e) {
      LOG.error(e);
      return false;
    }

    final URIish settingsUrl = root.getRepositoryFetchURL();
    if (settingsUrl == null) {
      return false;
    }
    if (uri.getHost() == null && settingsUrl.getHost() != null || uri.getHost() != null && !uri.getHost().equals(settingsUrl.getHost())) {
      return false;
    }
    if (uri.getPort() != settingsUrl.getPort()) {
      return false;
    }
    if (uri.getPath() == null && settingsUrl.getPath() != null || uri.getPath() != null && !uri.getPath().equals(settingsUrl.getPath())) {
      return false;
    }

    return true;
  }

  private String removeBranch(@NotNull final String url) {
    int branchSeparatorIndex = url.indexOf("#");
    return (branchSeparatorIndex > 0) ? url.substring(0, branchSeparatorIndex) : url;
  }

  public void invalidateRevisionsCache(@NotNull Repository db) {
    myCache.invalidateCache(db);
  }

  /**
   * Revisions cache for whole server.
   * ThreadSafe.
   */
  private final static class RevisionsCache {
    //repositoryId -> per repository cache
    private final ConcurrentMap<String, RepositoryRevisionCache> myCache = new ConcurrentHashMap<String, RepositoryRevisionCache>();
    private final int myRepositoryCacheSize;

    private RevisionsCache(int repositoryCacheSize) {
      myRepositoryCacheSize = repositoryCacheSize;
    }

    void invalidateCache(@NotNull final Repository db) {
      String repositoryId = getRepositoryId(db);
      RepositoryRevisionCache repositoryCache = myCache.get(repositoryId);
      if (repositoryCache != null)
        repositoryCache.removeNegativeEntries();
    }

    RepositoryRevisionCache getRepositoryCache(@NotNull final GitVcsRoot root) throws VcsException {
      String repositoryId = getRepositoryId(root);
      RepositoryRevisionCache result = myCache.get(repositoryId);
      if (result == null) {
        result = new RepositoryRevisionCache(myRepositoryCacheSize);
        RepositoryRevisionCache old = myCache.putIfAbsent(repositoryId, result);
        result = (old == null) ? result : old;
      }
      return result;
    }

    private String getRepositoryId(@NotNull final GitVcsRoot root) {
      return getRepositoryId(root.getRepositoryDir());
    }

    private String getRepositoryId(@NotNull final Repository db) {
      return getRepositoryId(db.getDirectory());
    }

    private String getRepositoryId(@NotNull final File repositoryDir) {
      try {
        return repositoryDir.getCanonicalPath();
      } catch (IOException e) {
        return repositoryDir.getAbsolutePath();
      }
    }

    @Override
    public String toString() {
      return myCache.toString();
    }
  }


  /**
   * Revisions cache for single repository.
   * ThreadSafe.
   */
  private final static class RepositoryRevisionCache {
    //revision (SHA) -> does this repository have such revision
    private final RecentEntriesCache<String, Boolean> myCache;

    private RepositoryRevisionCache(int cacheSize) {
      myCache = new RecentEntriesCache<String, Boolean>(cacheSize);
    }

    /**
     * @return true if repository has revision, false if doesn't, null if there is no data on this revision
     */
    @Nullable
    Boolean hasRevision(@NotNull String revision) {
      return myCache.get(revision);
    }

    void saveRevision(@NotNull String revision, boolean has) {
      myCache.put(revision, has);
    }

    @Override
    public String toString() {
      return myCache.toString();
    }

    void removeNegativeEntries() {
      myCache.removeValues(new Filter<Boolean>() {
        public boolean accept(@NotNull Boolean hasRevision) {
          return !hasRevision;//remove entries for commits we don't have
        }
      });
    }
  }


  private static class FullPath {
    private final String myPath;
    private final int myFirstSeparatorIdx;
    private final int myLastSeparatorIdx;
    private final boolean myValid;
    private final String myRevision;

    private FullPath(@NotNull String path) {
      myPath = path;
      myFirstSeparatorIdx = path.indexOf("|");
      myLastSeparatorIdx = path.lastIndexOf("|");
      myValid = myFirstSeparatorIdx >= 0 && myLastSeparatorIdx > myFirstSeparatorIdx;
      myRevision = myValid ? myPath.substring(0, myFirstSeparatorIdx).trim() : null;
    }

    boolean isValid() {
      return myValid;
    }

    @NotNull
    String getRevision() {
      if (!myValid)
        throw new IllegalStateException("Invalid path " + myPath);
      return myRevision;
    }

    boolean containsRevision() {
      if (!myValid)
        throw new IllegalStateException("Invalid path " + myPath);
      return !isEmpty(myRevision);
    }

    @NotNull
    String getRepositoryUrl() {
      return myPath.substring(myFirstSeparatorIdx + 1, myLastSeparatorIdx).trim();
    }

    @NotNull
    Collection<String> getMappedPaths() {
      return Collections.singleton(myPath.substring(myLastSeparatorIdx + 1).trim());
    }
  }
}
