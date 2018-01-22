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
public final class RevisionsCache {
  private static final Logger LOG = Logger.getInstance(RevisionsCache.class.getName());

  private ServerPluginConfig myConfig;
  //repositoryId -> per repository cache
  private final ConcurrentMap<String, RepositoryRevisionCache> myCache = new ConcurrentHashMap<>();
  private volatile int myRepositoriesCount;

  public RevisionsCache(@NotNull ServerPluginConfig config) {
    myConfig = config;
    if (config.persistentCacheEnabled())
      init(config.getCachesDir());
  }


  private void init(@NotNull File cachesDir) {
    File[] repoDirs = cachesDir.listFiles();
    if (repoDirs == null)
      return;
    myRepositoriesCount = repoDirs.length;
    for (File repoDir : repoDirs) {
      if (repoDir.isDirectory()) {
        for (RevisionCacheType type : RevisionCacheType.values()) {
          int cacheSize = getCacheSize(type);
          try {
            myCache.put(getRepositoryId(repoDir, type), RepositoryRevisionCache.read(myConfig, repoDir, type, cacheSize));
          } catch (Exception e) {
            LOG.warnAndDebugDetails("Error while initializing revisions cache for repository " + repoDir, e);
          }
        }
      }
    }
  }

  private int getCacheSize(@NotNull RevisionCacheType type) {
    if (type == RevisionCacheType.HINT_CACHE) {
      //if remote-run is used in all repositories, then hint cache should be able to store
      //at least one hint from every other repository, otherwise we will do redundant lookups.
      return Math.max(myRepositoriesCount * 2, myConfig.getMapFullPathRevisionCacheSize());
    } else {
      return myConfig.getMapFullPathRevisionCacheSize();
    }
  }


  public void resetNegativeEntries(@NotNull File repositoryDir) throws IOException {
    for (RevisionCacheType type : RevisionCacheType.values()) {
      String repositoryId = getRepositoryId(repositoryDir, type);
      RepositoryRevisionCache repositoryCache = myCache.get(repositoryId);
      if (repositoryCache != null)
        repositoryCache.resetNegativeEntries();
    }
  }


  public void resetNegativeEntries(@NotNull File repositoryDir, @NotNull Set<String> newCommits) throws IOException {
    for (RevisionCacheType type : RevisionCacheType.values()) {
      String repositoryId = getRepositoryId(repositoryDir, type);
      RepositoryRevisionCache repositoryCache = myCache.get(repositoryId);
      if (repositoryCache != null) {
        if (LOG.isDebugEnabled())
          LOG.debug("Invalidate cache for repository " + repositoryDir + ", new commits " + newCommits);
        repositoryCache.resetNegativeEntries(newCommits);
      }
    }
  }


  public void reset() {
    for (RepositoryRevisionCache repoCache : myCache.values()) {
      repoCache.reset();
    }
  }


  @NotNull
  public RepositoryRevisionCache getRepositoryCache(@NotNull File repositoryDir, @NotNull RevisionCacheType type) throws IOException {
    String repositoryId = getRepositoryId(repositoryDir, type);
    RepositoryRevisionCache result = myCache.get(repositoryId);
    if (result == null) {
      result = new RepositoryRevisionCache(myConfig, repositoryDir, type, getCacheSize(type));
      RepositoryRevisionCache old = myCache.putIfAbsent(repositoryId, result);
      result = (old == null) ? result : old;
    }
    return result;
  }


  @NotNull
  private String getRepositoryId(@NotNull File repositoryDir, @NotNull RevisionCacheType type) throws IOException {
    return repositoryDir.getAbsolutePath() + "_" + type.name();
  }
}
