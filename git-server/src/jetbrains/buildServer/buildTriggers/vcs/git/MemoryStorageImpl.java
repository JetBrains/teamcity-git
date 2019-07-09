/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Map file for URL => -Xmx for separate fetch process
 *
 * @author Mikhail Khorkov
 * @since 2019.2
 */
public class MemoryStorageImpl implements MemoryStorage {

  private static Logger LOG = Logger.getInstance(MemoryStorageImpl.class.getName());

  private final File myMapFile;
  private final File myCacheDir;

  private final Lock myLock = new ReentrantLock();
  private final Map<String, Long> myMemoryMap = new HashMap<>();

  public MemoryStorageImpl(@NotNull final ServerPluginConfig config) {
    myCacheDir = config.getCachesDir();
    myMapFile = new File(myCacheDir, "memory");
    loadMapFile();
  }

  private void loadMapFile() {
    myLock.lock();
    try {
      LOG.debug("Read a memory cache file " + myMapFile.getAbsolutePath());
      createMapFile();
      readMapFile();
    } finally {
      myLock.unlock();
    }
  }

  private void createMapFile() {
    myLock.lock();
    try {
      if (myCacheDir.exists() || myCacheDir.mkdir()) {
        try {
          if (!myMapFile.exists()) {
            myMapFile.createNewFile();
          }
        } catch (IOException e) {
          LOG.warn("Cannot create a memory cache file " + myMapFile.getAbsolutePath(), e);
        }
      }
    } finally {
      myLock.unlock();
    }
  }

  private void readMapFile() {
    myLock.lock();
    try {
      if (!myMapFile.exists()) {
        LOG.debug("A memory cache file " + myMapFile.getAbsolutePath() + "does not exist, work with empty cache");
      }

      for (String line : readFile(myMapFile)) {
        final String[] parts = line.split(" = ");
        if (parts.length != 2
            || parts[0] == null
            || parts[0].trim().length() == 0
            || parts[1] == null
            || parts[1].trim().length() == 0) {
          LOG.warn("Skip memory mapped line '" + line + "'");
          continue;
        }

        try {
          final Long value = Long.valueOf(parts[1]);
          myMemoryMap.put(parts[0], value);
        } catch (NumberFormatException e) {
          LOG.warn("Cannot parse memory value from line '" + line + "'");
        }
      }
    } finally {
      myLock.unlock();
    }
  }

  private List<String> readFile(@NotNull final File file) {
    myLock.lock();
    try {
      try {
        return FileUtil.readFile(file);
      } catch (IOException e) {
        LOG.error("Error while reading file " + file.getAbsolutePath(), e);
        return new ArrayList<>();
      }
    } finally {
      myLock.unlock();
    }
  }

  private void saveMapFile() {
    myLock.lock();
    try {
      if (!myMapFile.exists()) {
        LOG.debug("A memory cache file " + myMapFile.getAbsolutePath() + "does not exist and will do not be changed");
      }

      LOG.debug("Save the memory cache file " + myMapFile.getAbsolutePath());
      StringBuilder content = new StringBuilder();
      myMemoryMap.forEach((key, value) -> content.append(key).append(" = ").append(value).append("\n"));
      FileUtil.writeFileAndReportErrors(myMapFile, content.toString());
    } catch (IOException e) {
      LOG.error("Error while save a memory cache file " + myMapFile.getAbsolutePath(), e);
    } finally {
      myLock.unlock();
    }
  }

  @Override
  @Nullable
  public Long getCachedMemoryValue(@NotNull final String url) {
    myLock.lock();
    try {
      return myMemoryMap.get(url);
    } finally {
      myLock.unlock();
    }
  }

  @Override
  public void setCachedMemoryValue(@NotNull final String url, @NotNull final Long value) {
    myLock.lock();
    try {
      myMemoryMap.put(url, value);
      createMapFile();
      saveMapFile();
    } finally {
      myLock.unlock();
    }
  }

  /**
   * Remove memory value for specified url.
   *
   * @param url url to remove value
   */
  @Override
  public void deleteCachedMemoryValue(@NotNull final String url) {
    myLock.lock();
    try {
      myMemoryMap.remove(url);
      createMapFile();
      saveMapFile();
    } finally {
      myLock.unlock();
    }
  }
}
