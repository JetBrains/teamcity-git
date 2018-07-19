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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static java.util.Collections.singleton;

/**
* @author kir
*/
public class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());
  private final ServerPluginConfig myConfig;
  private final RevisionsCache myCache;
  private CommitLoader myCommitLoader;

  public GitMapFullPath(@NotNull ServerPluginConfig config, @NotNull RevisionsCache cache) {
    myConfig = config;
    myCache = cache;
  }


  public void setCommitLoader(@NotNull CommitLoader commitLoader) {
    myCommitLoader = commitLoader;
  }


  @NotNull
  public Collection<String> mapFullPath(@NotNull OperationContext context, @NotNull VcsRootEntry rootEntry, @NotNull String path) throws VcsException {
    GitVcsRoot root = context.getGitRoot(rootEntry.getVcsRoot());
    if (LOG.isDebugEnabled())
      LOG.debug("MapFullPath root: " + LogUtil.describe(root) + ", path " + path);
    FullPath fullPath = new FullPath(path);
    if (repositoryContainsPath(context, root, fullPath)) {
      return fullPath.getMappedPaths();
    } else {
      return Collections.emptySet();
    }
  }


  boolean repositoryContainsPath(@NotNull OperationContext context,
                                 @NotNull GitVcsRoot root,
                                 @NotNull FullPath path) throws VcsException {
    if (!path.isValid()) {
      LOG.warn("Invalid path: " + path);
      return false;
    }

    try {
      if (path.containsRevision()) {
        if (path.containsHintRevision()) {
          //if full path has a hint revision, first check if repository contains it;
          //a hint revision should rarely change and most likely will be cached
          return repositoryContainsRevision(context, root, path.getHintRevision(), RevisionCacheType.HINT_CACHE) &&
                 repositoryContainsRevision(context, root, path.getRevision(), RevisionCacheType.COMMIT_CACHE);
        } else {
          return repositoryContainsRevision(context, root, path.getRevision(), RevisionCacheType.COMMIT_CACHE);
        }
      } else {
        return urlsMatch(root, path);
      }
    } catch (IOException e) {
      LOG.error("Error while checking path suitability for root " + LogUtil.describe(root) + ", path: " + path.getPath(), e);
      return false;
    }
  }


  private boolean repositoryContainsRevision(@NotNull OperationContext context,
                                             @NotNull GitVcsRoot root,
                                             @NotNull String revision,
                                             @NotNull RevisionCacheType type) throws VcsException, IOException {
    RepositoryRevisionCache repositoryCache = myCache.getRepositoryCache(root.getRepositoryDir(), type);
    long resetCounter = repositoryCache.getResetCounter();
    Boolean hasRevision = repositoryCache.hasRevision(revision);
    if (hasRevision != null) {
      if (LOG.isDebugEnabled())
        LOG.debug("RevisionCache hit: root " + LogUtil.describe(root) + (hasRevision ? "contains " : "doesn't contain ") + "revision " + revision);
      return hasRevision;
    } else {
      if (LOG.isDebugEnabled())
        LOG.debug("RevisionCache miss: root " + LogUtil.describe(root) + ", revision " + revision + ", lookup commit in repository");
      hasRevision = myCommitLoader.findCommit(context.getRepository(root), revision) != null;
      if (LOG.isDebugEnabled())
        LOG.debug("Root " + LogUtil.describe(root) + ", revision " + revision + (hasRevision ? " was found" : " wasn't found") + ", cache the result");
      repositoryCache.saveRevision(revision, hasRevision, resetCounter);
      return hasRevision;
    }
  }


  private boolean urlsMatch(@NotNull GitVcsRoot root, @NotNull FullPath fullPath) {
    String url = removeBranch(fullPath.getRepositoryUrl());

    final URIish uri;
    try {
      uri = new URIish(url);
    } catch (final URISyntaxException e) {
      if (ReferencesResolverUtil.containsReference(url)) {
        LOG.warn("Unresolved parameter in url " + url + ", root " + LogUtil.describe(root));
      } else {
        LOG.warnAndDebugDetails("Error while parsing VCS root url " + url + ", root " + LogUtil.describe(root), e);
      }
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

  @NotNull
  private String removeBranch(@NotNull final String url) {
    int branchSeparatorIndex = url.indexOf("#");
    return (branchSeparatorIndex > 0) ? url.substring(0, branchSeparatorIndex) : url;
  }

  public void invalidateRevisionsCache(@NotNull Repository db, @NotNull Map<String, Ref> oldRefs, @NotNull Map<String, Ref> newRefs) throws IOException {
    try {
      if (myConfig.ignoreFetchedCommits()) {
        myCache.resetNegativeEntries(db.getDirectory());
      } else {
        Set<String> newCommits = getNewCommits(db, oldRefs, newRefs);
        myCache.resetNegativeEntries(db.getDirectory(), newCommits);
      }
    } catch (IOException e) {
      LOG.warn("Error while resetting commits cache for repository " + db.getDirectory(), e);
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

    if (updatedHeads.isEmpty()) {
      // avoid expensive RevWalk.parseAny for uninteresting heads if there are no updated heads
      return Collections.emptySet();
    }

    RevWalk revWalk = new RevWalk(db);
    try {
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
      RevCommit newCommit;
      while ((newCommit = revWalk.next()) != null) {
        newCommits.add(newCommit.name());
      }
      return newCommits;
    } finally {
      revWalk.dispose();
    }
  }


  //Format: <hint revision>-<git revision hash>|<repository url>|<file relative path>
  public static class FullPath {
    private final String myPath;
    private final int myFirstSeparatorIdx;
    private final int myLastSeparatorIdx;
    private final boolean myValid;
    private final String myRevision;
    private final String myHintRevision;

    public FullPath(@NotNull String path) {
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
      return singleton(myPath.substring(myLastSeparatorIdx + 1).trim());
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof FullPath)) return false;
      final FullPath fullPath = (FullPath)o;
      return myPath.equals(fullPath.myPath);
    }

    @Override
    public int hashCode() {
      return myPath.hashCode();
    }
  }
}
