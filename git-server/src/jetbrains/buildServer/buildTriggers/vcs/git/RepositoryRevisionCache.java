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

import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Revisions cache for single repository.
 * ThreadSafe.
 */
public final class RepositoryRevisionCache {
  private final ServerPluginConfig myConfig;
  private final File myRepositoryDir;
  private final RevisionCacheType myType;
  private final RecentEntriesCache<String, Boolean> myCache;//revision (SHA) -> does this repository have such revision
  private final AtomicLong myResetCounter = new AtomicLong(0);

  public RepositoryRevisionCache(@NotNull ServerPluginConfig config,
                                 @NotNull File repositoryDir,
                                 @NotNull RevisionCacheType type,
                                 int cacheSize) {
    myConfig = config;
    myRepositoryDir = repositoryDir;
    myType = type;
    myCache = new RecentEntriesCache<>(cacheSize);
  }

  /**
   * @return true if repository has revision, false if doesn't, null if there is no data on this revision
   */
  @Nullable
  public Boolean hasRevision(@NotNull String revision) {
    return myCache.get(revision);
  }


  public void saveRevision(@NotNull String revision, boolean has, long expectedResetCounter) throws IOException {
    synchronized (myCache) {
      if (myResetCounter.get() != expectedResetCounter)
        return;
      Boolean existing = hasRevision(revision);
      if (existing == null || has != existing) {
        saveRevision(revision, has);
        write();
      }
    }
  }


  private void saveRevision(@NotNull String revision, boolean has) throws IOException {
    myCache.put(revision, has);
  }


  void resetNegativeEntries() throws IOException {
    synchronized (myCache) {
      myResetCounter.incrementAndGet();
      AtomicBoolean updated = new AtomicBoolean(false);
      myCache.removeValues(hasValue -> {
        if (!hasValue) {
          updated.set(true);
          return true;
        } else {
          return false;
        }
      });
      if (updated.get())
        write();
    }
  }


  void resetNegativeEntries(@NotNull Set<String> newCommits) throws IOException {
    synchronized (myCache) {
      //we should increment reset counter even if newCommits were not cached,
      //because concurrent map-full-path might be about to cache the commit
      myResetCounter.incrementAndGet();

      //instead of removing negative entries - turn them into positive, this saves 1 commit lookup
      Set<String> forUpdate = new HashSet<>();
      myCache.forEachEntry((commit, contains) -> {
        if (newCommits.contains(commit) && Boolean.FALSE.equals(contains))
          forUpdate.add(commit);
        return true;
      });
      for (String commit : forUpdate) {
        myCache.put(commit, true);
      }
      if (!forUpdate.isEmpty())
        write();
    }
  }


  public void reset() {
    synchronized (myCache) {
      myResetCounter.incrementAndGet();
      myCache.clear();
      FileUtil.delete(getCacheFile(myRepositoryDir, myType));
    }
  }


  public long getResetCounter() {
    return myResetCounter.get();
  }


  private void write() throws IOException {
    if (!myConfig.persistentCacheEnabled()) {
      FileUtil.delete(getCacheFile(myRepositoryDir, myType));
      return;
    }

    File cache = getCacheFile(myRepositoryDir, myType);
    cache.getParentFile().mkdirs();
    try (PrintStream printer = new PrintStream(new BufferedOutputStream(new FileOutputStream(cache)))) {
      myCache.forEachEntry((revision, contains) -> {
        if (contains != null) {
          printer.print(contains ? '+' : '-');
          printer.print(revision);
          printer.println();
        }
        return true;
      });
    }
  }


  public boolean equals(Object o) {
    if (!(o instanceof RepositoryRevisionCache))
      return false;

    RepositoryRevisionCache other = (RepositoryRevisionCache) o;
    if (myType != other.myType)
      return false;

    if (!myRepositoryDir.equals(other.myRepositoryDir))
      return false;

    Set<String> processed = new HashSet<>();
    for (String revision : myCache.keySet()) {
      Boolean contains1 = hasRevision(revision);
      Boolean contains2 = other.hasRevision(revision);
      if (!Objects.equals(contains1, contains2))
        return false;
      processed.add(revision);
    }
    for (String revision : other.myCache.keySet()) {
      if (processed.add(revision) && other.hasRevision(revision) != null)
        return false;
    }
    return true;
  }


  @Override
  public String toString() {
    return myRepositoryDir.getAbsolutePath() + " " + myCache.toString();
  }


  @NotNull
  public static RepositoryRevisionCache read(@NotNull ServerPluginConfig config,
                                             @NotNull File repositoryDir,
                                             @NotNull RevisionCacheType type,
                                             int size) throws IOException {
    File cache = getCacheFile(repositoryDir, type);
    if (cache.isFile()) {
      RepositoryRevisionCache result = new RepositoryRevisionCache(config, repositoryDir, type, size);
      for (String line : FileUtil.readFile(cache)) {
        if (!line.isEmpty()) {
          char c = line.charAt(0);
          switch (c) {
            case '+':
              result.saveRevision(line.substring(1), true);
              break;
            case '-':
              result.saveRevision(line.substring(1), false);
              break;
            default:
              throw new IOException("Bad cache line '" + line + "'");
          }
        }
      }
      return result;
    } else {
      return new RepositoryRevisionCache(config, repositoryDir, type, size);
    }
  }


  @NotNull
  public static File getCacheFile(@NotNull File repositoryDir, @NotNull RevisionCacheType type) {
    File cachesDir = new File(repositoryDir, "caches");
    return new File(cachesDir, type.getFileName());
  }
}
