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

import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Revisions cache for single repository.
 * ThreadSafe.
 */
final class RepositoryRevisionCache {
  //revision (SHA) -> does this repository have such revision
  private final RecentEntriesCache<String, Boolean> myCache;

  RepositoryRevisionCache(int cacheSize) {
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
