/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author dmitry.neverov
 */
public class MirrorManagerImpl implements MirrorManager {

  private static Logger LOG = Logger.getInstance(MirrorManagerImpl.class.getName());

  private final File myBaseMirrorsDir;
  private final File myMapFile;
  /*url -> dir name*/
  private final ConcurrentMap<String, String> myMirrorMap = new ConcurrentHashMap<String, String>();
  private final Object myLock = new Object();
  private final HashCalculator myHashCalculator;


  public MirrorManagerImpl(@NotNull final PluginConfig config, @NotNull HashCalculator hash) {
    myHashCalculator = hash;
    myBaseMirrorsDir = config.getCachesDir();
    myMapFile = new File(myBaseMirrorsDir, "map");
    loadMappings();
  }


  @NotNull
  public File getBaseMirrorsDir() {
    return myBaseMirrorsDir;
  }


  @NotNull
  public File getMirrorDir(@NotNull String repositoryUrl) {
    return new File(myBaseMirrorsDir, getDirNameForUrl(repositoryUrl));
  }


  /**
   * Returns repository dir name for specified url. Every url gets unique dir name.
   * @param url url of interest
   * @return see above
   */
  @NotNull
  private String getDirNameForUrl(@NotNull final String url) {
    String dirName = myMirrorMap.get(url);
    if (dirName != null)
      return dirName;
    synchronized (myLock) {
      String existing = myMirrorMap.get(url);
      if (existing != null)
        return existing;
      dirName = getUniqueDirNameForUrl(url);
      myMirrorMap.put(url, dirName);
      saveMappingToFile();
      return dirName;
    }
  }


  @NotNull
  private String getUniqueDirNameForUrl(@NotNull final String url) {
    String dirName = calculateDirNameForUrl(url);
    int i = 0;
    synchronized (myLock) {
      while(isOccupiedDirName(dirName)) {
        dirName = calculateDirNameForUrl(url + i);
        i++;
      }
    }
    return dirName;
  }


  @NotNull
  private String calculateDirNameForUrl(@NotNull String url) {
    return String.format("git-%08X.git", myHashCalculator.getHash(url) & 0xFFFFFFFFL);
  }


  private boolean isOccupiedDirName(@NotNull final String dirName) {
    return myMirrorMap.values().contains(dirName) || new File(myBaseMirrorsDir, dirName).exists();
  }


  private void saveMappingToFile() {
    LOG.debug("Save mapping to " + myMapFile.getAbsolutePath());
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> mirror : myMirrorMap.entrySet()) {
      String url = mirror.getKey();
      String dir = mirror.getValue();
      sb.append(url).append(" = ").append(dir).append("\n");
    }
    synchronized (myLock) {
      FileUtil.writeFile(myMapFile, sb.toString());
    }
  }


  private void loadMappings() {
    synchronized (myLock) {
      LOG.debug("Parse mapping file " + myMapFile.getAbsolutePath());
      if (myMapFile.exists()) {
        readMappings();
      } else {
        createMapFile();
      }
    }
  }


  private void readMappings() {
    synchronized (myLock) {
      for (String line : readLines()) {
        int separatorIndex = line.lastIndexOf(" = ");
        if (separatorIndex == -1) {
          if (!line.equals(""))
            LOG.warn("Cannot parse mapping '" + line + "', skip it.");
        } else {
          String url = line.substring(0, separatorIndex);
          String dirName = line.substring(separatorIndex + 3);
          if (myMirrorMap.values().contains(dirName)) {
            LOG.error("Skip mapping " + line + ": " + dirName + " is used for url other than " + url);
          } else {
            myMirrorMap.put(url, dirName);
          }
        }
      }
    }
  }


  private List<String> readLines() {
    synchronized (myLock) {
      try {
        return FileUtil.readFile(myMapFile);
      } catch (IOException e) {
        LOG.error("Error while reading a mapping file at " + myMapFile.getAbsolutePath() + " starting with empty mapping", e);
        return new ArrayList<String>();
      }
    }
  }


  private void createMapFile() {
    synchronized (myLock) {
      LOG.info("No mapping file found at " + myMapFile.getAbsolutePath() + ", create a new one");
      if (!myBaseMirrorsDir.exists() && !myBaseMirrorsDir.mkdirs()) {
        LOG.error("Cannot create base mirrors dir at " + myBaseMirrorsDir.getAbsolutePath() + ", start with empty mapping");
      } else {
        try {
          if (myMapFile.createNewFile()) {
            restoreMapFile();
          } else {
            LOG.warn("Someone else creates a mapping file " + myMapFile.getAbsolutePath() + ", will use it");
            readLines();
          }
        } catch (IOException e) {
          LOG.error("Cannot create a mapping file at " + myMapFile.getAbsolutePath() + ", start with empty mapping", e);
        }
      }
    }
  }


  private void restoreMapFile() {
    LOG.info("Restore mapping from existing repositories");
    Map<String, String> restoredMappings = restoreMappings();
    myMirrorMap.putAll(restoredMappings);
    saveMappingToFile();
  }


  @NotNull
  private Map<String, String> restoreMappings() {
    Map<String, String> result = new HashMap<String, String>();
    File[] subDirs = findRepositoryDirs();
    if (subDirs.length > 0) {
      LOG.info(subDirs.length + " existing repositories found");
      for (File dir : subDirs) {
        String url = getRemoteRepositoryUrl(dir);
        if (url != null) {
          result.put(url, dir.getName());
        } else {
          LOG.warn("Cannot retrieve remote repository url for " + dir.getName() + ", skip it");
        }
      }
    } else {
      LOG.info("No existing repositories found");
    }
    return result;
  }


  @NotNull
  private File[] findRepositoryDirs() {
    return myBaseMirrorsDir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        return f.isDirectory() && new File(f, "config").exists();
      }
    });
  }


  @Nullable
  private String getRemoteRepositoryUrl(@NotNull final File repositoryDir) {
    try {
      Repository r = new RepositoryBuilder().setBare().setGitDir(repositoryDir).build();
      StoredConfig config = r.getConfig();
      String teamcityRemote = config.getString("teamcity", null, "remote");
      if (teamcityRemote != null)
        return teamcityRemote;
      return config.getString("remote", "origin", "url");
    } catch (Exception e) {
      LOG.warn("Error while trying to get remote repository url at " + repositoryDir.getAbsolutePath(), e);
      return null;
    }
  }
}
