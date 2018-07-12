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
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.getWrongUrlError;

/**
 * @author dmitry.neverov
 */
public final class RepositoryManagerImpl implements RepositoryManager {

  private static final Logger LOG = Logger.getInstance(RepositoryManagerImpl.class.getName());

  private final MirrorManager myMirrorManager;
  private final long myExpirationTimeout;
  /**
   * During repository creation jgit checks existence of some files and directories. When several threads
   * try to create repository concurrently some of them could see it in inconsistent state. This map contains
   * locks for repository creation, so only one thread at a time will create repository at give dir.
   */
  private final ConcurrentMap<String, Object> myCreateLocks = new ConcurrentHashMap<>();
  /**
   * In the past jgit has some concurrency problems, in order to fix them we do only one fetch at a time.
   * Also several concurrent fetches in single repository does not make sense since only one of them succeed.
   * This map contains locks used for fetch and push operations.
   */
  private final ConcurrentMap<String, Object> myWriteLocks = new ConcurrentHashMap<>();
  /**
   * During cleanup unused bare repositories are removed. This map contains rw locks for repository removal.
   * Fetch/push/create operations should be done with read lock hold, remove operation is done with write lock hold.
   * @see Cleanup
   */
  private final ConcurrentMap<String, ReadWriteLock> myRmLocks = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, Object> myUpdateLastUsedTimeLocks = new ConcurrentHashMap<>();

  //repo dir -> last access time (nano seconds)
  private final ConcurrentMap<File, Long> myLastAccessTime = new ConcurrentHashMap<>();

  private final AutoCloseRepositoryCache myRepositoryCache = new AutoCloseRepositoryCache();

  private final ServerPluginConfig myConfig;

  public RepositoryManagerImpl(@NotNull final ServerPluginConfig config, @NotNull final MirrorManager mirrorManager) {
    myConfig = config;
    myExpirationTimeout = config.getMirrorExpirationTimeoutMillis();
    myMirrorManager = mirrorManager;
  }


  @NotNull
  public File getBaseMirrorsDir() {
    return myMirrorManager.getBaseMirrorsDir();
  }


  @NotNull
  public File getMirrorDir(@NotNull String repositoryUrl) {
    return myMirrorManager.getMirrorDir(repositoryUrl);
  }


  public void invalidate(@NotNull final File dir) {
    myMirrorManager.invalidate(dir);
  }


  @NotNull
  public Map<String, File> getMappings() {
    return myMirrorManager.getMappings();
  }

  @Nullable
  @Override
  public String getUrl(@NotNull String cloneDirName) {
    return myMirrorManager.getUrl(cloneDirName);
  }

  @NotNull
  public List<File> getExpiredDirs() {
    long now = System.currentTimeMillis();
    List<File> result = new ArrayList<File>();
    final File[] files = myMirrorManager.getBaseMirrorsDir().listFiles();
    if (files == null)
      return result;
    for (File f : files) {
      if (f.isDirectory() && isExpired(f, now))
        result.add(f);
    }
    return result;
  }


  private boolean isExpired(@NotNull final File dir, long now) {
    long lastUsedTime = getLastUsedTime(dir);
    return now - lastUsedTime > myExpirationTimeout;
  }


  public long getLastUsedTime(@NotNull File dir) {
    return myMirrorManager.getLastUsedTime(dir);
  }

  @NotNull
  public Repository openRepository(@NotNull final URIish fetchUrl) throws VcsException {
    final URIish canonicalURI = getCanonicalURI(fetchUrl);
    final File dir = getMirrorDir(canonicalURI.toString());
    return openRepository(dir, canonicalURI);
  }


  @NotNull
  public Repository openRepository(@NotNull final File dir, @NotNull final URIish fetchUrl) throws VcsException {
    final URIish canonicalURI = getCanonicalURI(fetchUrl);
    if (isDefaultMirrorDir(dir))
      updateLastUsedTime(dir);
    Repository result = myRepositoryCache.get(RepositoryCache.FileKey.exact(dir, FS.DETECTED));
    if (result == null)
      return createRepository(dir, canonicalURI);
    String existingRemote = result.getConfig().getString("teamcity", null, "remote");
    if (existingRemote == null) {
      myRepositoryCache.release(result);
      invalidate(dir);
      return GitServerUtil.getRepository(dir, fetchUrl);
    }
    if (!canonicalURI.toString().equals(existingRemote)) {
      myRepositoryCache.release(result);
      throw getWrongUrlError(dir, existingRemote, fetchUrl);
    }
    return result;
  }

  public void closeRepository(@NotNull Repository repository) {
    myRepositoryCache.release(repository);
  }

  @NotNull
  private Repository createRepository(@NotNull final File dir, @NotNull final URIish fetchUrl) throws VcsException {
    return runWithDisabledRemove(dir, () -> {
      synchronized (getCreateLock(dir)) {
        Repository result = GitServerUtil.getRepository(dir, fetchUrl);
        return myRepositoryCache.add(RepositoryCache.FileKey.exact(dir, FS.DETECTED), result);
      }
    });
  }


  private void updateLastUsedTime(@NotNull final File dir) {
    Long timeNano = myLastAccessTime.get(dir);
    //don't update last used time too often to decrease file-system activity
    if (timeNano != null && TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - timeNano) < myConfig.getAccessTimeUpdateRateMinutes()) {
      return;
    }
    Lock rmLock = getRmLock(dir).readLock();
    rmLock.lock();
    try {
      synchronized (getUpdateLastUsedTimeLock(dir)) {
        File timestamp = new File(dir, "timestamp");
        if (!dir.exists() && !dir.mkdirs())
          throw new IOException("Cannot create directory " + dir.getAbsolutePath());
        if (!timestamp.exists())
          timestamp.createNewFile();
        FileUtil.writeFileAndReportErrors(timestamp, String.valueOf(System.currentTimeMillis()));
        myLastAccessTime.put(dir, System.nanoTime());
      }
    } catch (IOException e) {
      LOG.error("Error while updating timestamp in " + dir.getAbsolutePath(), e);
    } finally {
      rmLock.unlock();
    }
  }


  private boolean isDefaultMirrorDir(@NotNull final File dir) {
    File baseDir = myMirrorManager.getBaseMirrorsDir();
    return baseDir.equals(dir.getParentFile());
  }


  @NotNull
  private Object getUpdateLastUsedTimeLock(@NotNull File dir) {
    return getOrCreate(myUpdateLastUsedTimeLocks, getCanonicalName(dir), new Object());
  }


  @NotNull
  public Object getWriteLock(@NotNull final File dir) {
    return getOrCreate(myWriteLocks, getCanonicalName(dir), new Object());
  }


  @NotNull
  public ReadWriteLock getRmLock(@NotNull final File dir) {
    return getOrCreate(myRmLocks, getCanonicalName(dir), new ReentrantReadWriteLock());
  }

  @NotNull
  private String getCanonicalName(final @NotNull File dir) {
    String name = dir.getName();
    if (".".equals(name) || "..".equals(name)) {
      // call getCanonical in special cases only
      return FileUtil.getCanonicalFile(dir).getName();
    }

    return name;
  }


  @Override
  public <T> T runWithDisabledRemove(@NotNull File dir, @NotNull VcsOperation<T> operation) throws VcsException {
    Lock readLock = getRmLock(dir).readLock();
    readLock.lock();
    try {
      return operation.run();
    } finally {
      readLock.unlock();
    }
  }


  @Override
  public void runWithDisabledRemove(@NotNull File dir, @NotNull VcsAction action) throws VcsException {
    Lock readLock = getRmLock(dir).readLock();
    readLock.lock();
    try {
      action.run();
    } finally {
      readLock.unlock();
    }
  }

  @NotNull
  public Object getCreateLock(File dir) {
    return getOrCreate(myCreateLocks, getCanonicalName(dir), new Object());
  }


  public void cleanLocksFor(@NotNull final File dir) {
    final String canonicalName = getCanonicalName(dir);
    myWriteLocks.remove(canonicalName);
    myCreateLocks.remove(canonicalName);
    myRmLocks.remove(canonicalName);
  }

  private <K, V> V getOrCreate(ConcurrentMap<K, V> map, K key, V value) {
    V existing = map.putIfAbsent(key, value);
    if (existing != null)
      return existing;
    else
      return value;
  }


  @NotNull
  private URIish getCanonicalURI(@NotNull final URIish uri) {
    return uri;
//    return new URIish()
//      .setScheme(uri.getScheme())
//      .setHost(uri.getHost())
//      .setPort(uri.getPort())
//      .setPath(uri.getPath());
  }
}
