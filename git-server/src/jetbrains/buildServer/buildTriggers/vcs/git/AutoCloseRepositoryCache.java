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

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache of repositories.
 *
 * Tracks number of usages of the repository and once it becomes
 * zero repository is closed and removed from the cache.
 */
final class AutoCloseRepositoryCache {

  private final Map<RepositoryCache.FileKey, CachedRepository> myRepositories = new HashMap<RepositoryCache.FileKey, CachedRepository>();

  /**
   * Returns a repository for the given key or null if repository is not found
   * in cache. If repository is found its openCounter is incremented. When the
   * caller is done with repository it must call {@link #release}
   * method.
   * @param key repository key
   * @return see above
   */
  @Nullable
  synchronized Repository get(@NotNull RepositoryCache.FileKey key) {
    CachedRepository cachedRepository = myRepositories.get(key);
    if (cachedRepository != null) {
      Repository result = cachedRepository.getRepository();
      cachedRepository.inc();
      return result;
    }
    return null;
  }

  /**
   * Adds a new repository with the specified key in cache. Returns the added
   * repository if there was no repository in cache associated with the given
   * key, otherwise existing repository associated with the key is returned
   * and its openCounter is incremented. When the caller is done with repository
   * it must call the {@link #release} method.
   * @param key repository key
   * @param db repository
   * @return see above
   */
  @NotNull
  synchronized Repository add(@NotNull RepositoryCache.FileKey key, @NotNull Repository db) {
    CachedRepository existing = myRepositories.get(key);
    if (existing == null) {
      myRepositories.put(key, new CachedRepository(db));
      return db;
    } else {
      Repository result = existing.getRepository();
      existing.inc();
      return result;
    }
  }

  /**
   * Releases the repository acquired via {@link #add} or {@link #get} method.
   * Decrements an openCounter for the repository and if it reaches 0 repository
   * is closed and removed from the cache. Does nothing if repository is not
   * present found in the cache.
   * @param db repository to release
   */
  synchronized void release(@NotNull Repository db) {
    RepositoryCache.FileKey key = RepositoryCache.FileKey.exact(db.getDirectory(), FS.DETECTED);
    CachedRepository cachedRepository = myRepositories.get(key);
    if (cachedRepository != null && cachedRepository.getRepository() == db && cachedRepository.dec() == 0) {
      myRepositories.remove(key);
      db.close();
    }
  }

  private final static class CachedRepository {
    private final Repository myRepository;
    private final AtomicInteger myOpenCounter = new AtomicInteger(1);
    public CachedRepository(@NotNull Repository repository) {
      myRepository = repository;
    }
    @NotNull
    public Repository getRepository() {
      return myRepository;
    }
    public void inc() {
      myOpenCounter.incrementAndGet();
    }
    public int dec() {
      return myOpenCounter.decrementAndGet();
    }
  }
}
