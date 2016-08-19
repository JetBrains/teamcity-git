/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Revisions cache for whole server.
 * ThreadSafe.
 */
final class RevisionsCache {
  private static final Logger LOG = Logger.getInstance(RevisionsCache.class.getName());

  //repositoryId -> per repository cache
  private final ConcurrentMap<String, RepositoryRevisionCache> myCache = new ConcurrentHashMap<String, RepositoryRevisionCache>();
  private final int myRepositoryCacheSize;

  RevisionsCache(int repositoryCacheSize) {
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
