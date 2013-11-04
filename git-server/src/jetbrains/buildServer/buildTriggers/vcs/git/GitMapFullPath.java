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
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
* @author kir
*/
public class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());
  private final ServerPluginConfig myConfig;
  private final RevisionsCache myCache;
  private CommitLoader myCommitLoader;

  public GitMapFullPath(@NotNull ServerPluginConfig config) {
    myConfig = config;
    myCache = new RevisionsCache(config.getMapFullPathRevisionCacheSize());
  }


  public void setCommitLoader(@NotNull CommitLoader commitLoader) {
    myCommitLoader = commitLoader;
  }


  public Collection<String> mapFullPath(@NotNull OperationContext context, @NotNull VcsRootEntry rootEntry, @NotNull String path) throws VcsException {
    GitVcsRoot root = context.getGitRoot();
    LOG.debug("MapFullPath root: " + LogUtil.describe(root) + ", path " + path);
    FullPath fullPath = new FullPath(path);
    if (!fullPath.isValid()) {
      LOG.warn("Invalid path: " + path);
      return Collections.emptySet();
    }

    //match by revision
    if (fullPath.containsRevision()) {
      if (fullPath.containsHintRevision()) {
        //if full path has a hint revision, first check if repository contains it;
        //a hint revision should rarely change and most likely will be cached
        if (repositoryContainsRevision(context, rootEntry, fullPath.getHintRevision())
            && repositoryContainsRevision(context, rootEntry, fullPath.getRevision()))
            return fullPath.getMappedPaths();
      } else {
        if (repositoryContainsRevision(context, rootEntry, fullPath.getRevision()))
          return fullPath.getMappedPaths();
      }
    }

    //match by url only if path doesn't have revision
    if (!fullPath.containsRevision() && urlsMatch(root, fullPath))
      return fullPath.getMappedPaths();

    return Collections.emptySet();
  }


  private boolean repositoryContainsRevision(@NotNull OperationContext context, @NotNull VcsRootEntry rootEntry, @NotNull String revision) throws VcsException {
    GitVcsRoot root = context.getGitRoot();
    RepositoryRevisionCache repositoryCache = myCache.getRepositoryCache(root);
    Boolean hasRevision = repositoryCache.hasRevision(revision);
    if (hasRevision != null) {
      LOG.debug("RevisionCache hit: root " + LogUtil.describe(rootEntry.getVcsRoot()) + (hasRevision ? "contains " : "doesn't contain ") + "revision " + revision);
      return hasRevision;
    } else {
      LOG.debug("RevisionCache miss: root " + LogUtil.describe(rootEntry.getVcsRoot()) + ", revision " + revision + ", lookup commit in repository");
      hasRevision = myCommitLoader.findCommit(context.getRepository(), revision) != null;
      LOG.debug("Root " + LogUtil.describe(rootEntry.getVcsRoot()) + ", revision " + revision + (hasRevision ? " was found" : " wasn't found") + ", cache the result");
      repositoryCache.saveRevision(revision, hasRevision);
      return hasRevision;
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

  public void invalidateRevisionsCache(@NotNull Repository db, @NotNull Map<String, Ref> oldRefs, @NotNull Map<String, Ref> newRefs) {
    if (myConfig.ignoreFetchedCommits()) {
      myCache.invalidateCache(db);
    } else {
      try {
        Set<String> newCommits = getNewCommits(db, oldRefs, newRefs);
        myCache.invalidateCache(db, newCommits);
      } catch (IOException e) {
        LOG.warn("Error while calculating new commits for repository " + db.getDirectory(), e);
        myCache.invalidateCache(db);
      }
    }
  }

  private Set<String> getNewCommits(@NotNull Repository db, @NotNull Map<String, Ref> oldRefs, @NotNull Map<String, Ref> newRefs) throws IOException {
    Set<ObjectId> updatedHeads = new HashSet<ObjectId>();
    Set<ObjectId> uninteresting = new HashSet<ObjectId>();
    for (Map.Entry<String, Ref> e : newRefs.entrySet()) {
      String refName = e.getKey();
      if (!refName.startsWith("refs/"))
        continue;
      Ref newRef = e.getValue();
      Ref oldRef = oldRefs.get(refName);
      if (oldRef == null || !oldRef.getObjectId().equals(newRef.getObjectId()))
        updatedHeads.add(newRef.getObjectId());
      if (oldRef != null)
        uninteresting.add(oldRef.getObjectId());
    }

    RevWalk revWalk = new RevWalk(db);
    revWalk.sort(RevSort.TOPO);
    for (ObjectId id : updatedHeads) {
      RevObject obj = revWalk.parseAny(id);
      if (obj.getType() == Constants.OBJ_COMMIT)
        revWalk.markStart((RevCommit) obj);
    }
    for (ObjectId id : uninteresting) {
      RevObject obj = revWalk.parseAny(id);
      if (obj.getType() == Constants.OBJ_COMMIT)
        revWalk.markUninteresting((RevCommit) obj);
    }
    Set<String> newCommits = new HashSet<String>();
    RevCommit newCommit = null;
    while ((newCommit = revWalk.next()) != null) {
      newCommits.add(newCommit.name());
    }
    return newCommits;
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

    void invalidateCache(@NotNull final Repository db, @NotNull Set<String> newCommits) {
      String repositoryId = getRepositoryId(db);
      RepositoryRevisionCache repositoryCache = myCache.get(repositoryId);
      if (repositoryCache != null) {
        if (LOG.isDebugEnabled())
          LOG.debug("Invalidate cache for repository " + db.getDirectory() + ", new commits " + newCommits);
        repositoryCache.removeNegativeEntries(newCommits);
      }
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

    void removeNegativeEntries(@NotNull Set<String> newCommits) {
      synchronized (myCache) {
        Set<String> forRemove = new HashSet<String>();
        for (String commit : myCache.keySet()) {
          if (newCommits.contains(commit) && Boolean.FALSE.equals(myCache.get(commit)))
            forRemove.add(commit);
        }

        for (String commit : forRemove) {
          myCache.remove(commit);
        }
      }
    }
  }

  //Format: <hint revision>-<git revision hash>|<repository url>|<file relative path>
  private static class FullPath {
    private final String myPath;
    private final int myFirstSeparatorIdx;
    private final int myLastSeparatorIdx;
    private final boolean myValid;
    private final String myRevision;
    private final String myHintRevision;

    private FullPath(@NotNull String path) {
      myPath = path;
      myFirstSeparatorIdx = path.indexOf("|");
      myLastSeparatorIdx = path.lastIndexOf("|");
      myValid = myFirstSeparatorIdx >= 0 && myLastSeparatorIdx > myFirstSeparatorIdx;
      Pair<String, String> revisions = parseRevisions();
      myHintRevision = revisions.first;
      myRevision = revisions.second;
    }

    private Pair<String, String> parseRevisions() {
      if (!myValid)
        return Pair.create(null, null);
      String revisions = myPath.substring(0, myFirstSeparatorIdx).trim();
      int idx = revisions.indexOf("-");
      if (idx <= 0)
        return Pair.create(null, revisions);
      return Pair.create(revisions.substring(0, idx), revisions.substring(idx + 1, revisions.length()));
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

    @Nullable
    String getHintRevision() {
      return myHintRevision;
    }

    boolean containsHintRevision() {
      if (!myValid)
        throw new IllegalStateException("Invalid path " + myPath);
      return myHintRevision != null;
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
