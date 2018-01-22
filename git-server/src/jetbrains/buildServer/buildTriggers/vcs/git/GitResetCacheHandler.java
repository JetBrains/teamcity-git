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
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static com.intellij.openapi.util.io.FileUtil.delete;
import static java.util.Collections.singletonList;

/**
 * @author dmitry.neverov
 */
public class GitResetCacheHandler implements ResetCacheHandler {

  private static Logger LOG = Logger.getInstance(GitResetCacheHandler.class.getName());
  private final static String GIT_CACHE_NAME = "git";

  private final RepositoryManager myRepositoryManager;
  private final GcErrors myGcErrors;
  private AtomicBoolean myResetRunning = new AtomicBoolean(false);

  public GitResetCacheHandler(@NotNull RepositoryManager repositoryManager,
                              @NotNull GcErrors gcErrors) {
    myRepositoryManager = repositoryManager;
    myGcErrors = gcErrors;
  }

  @NotNull
  public List<String> listCaches() {
    return singletonList(GIT_CACHE_NAME);
  }

  public boolean isEmpty(@NotNull String cache) {
    return myRepositoryManager.getMappings().isEmpty();
  }

  public void resetCache(@NotNull String cache) {
    boolean started = startReset();
    if (!started) {
      LOG.info("Git mirrors reset is already running");
      return;
    }
    resetAllMirrors();
    finishReset();
  }

  private boolean startReset() {
    return myResetRunning.compareAndSet(false, true);
  }

  private void finishReset() {
    myResetRunning.set(false);
  }

  private void resetAllMirrors() {
    LOG.info("Start resetting git caches");
    for (Map.Entry<String, File> entry : myRepositoryManager.getMappings().entrySet()) {
      String url = entry.getKey();
      File mirror = entry.getValue();
      Lock writeLock = myRepositoryManager.getRmLock(mirror).writeLock();
      writeLock.lock();
      try {
        resetMirror(mirror, url);
      } finally {
        writeLock.unlock();
      }
    }
    LOG.info("Git caches reset");
  }

  private void resetMirror(@NotNull File mirror, @NotNull String url) {
    LOG.debug("Delete of the repository " + url + " (" + mirror.getAbsolutePath() + ")");
    delete(mirror);
    myGcErrors.clearError(mirror);
  }
}
