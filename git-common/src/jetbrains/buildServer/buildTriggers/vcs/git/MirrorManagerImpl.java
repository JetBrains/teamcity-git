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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author dmitry.neverov
 */
public class MirrorManagerImpl implements MirrorManager {

  private static Logger LOG = Logger.getInstance(MirrorManagerImpl.class.getName());

  private final File myBaseMirrorsDir;
  private final File myMapFile;
  private final File myInvalidDirsFile;
  /*url -> dir name*/
  private final Map<String, String> myMirrorMap = new HashMap<String, String>();
  private final Set<String> myInvalidDirNames = new HashSet<String>();
  private final Object myLock = new Object();
  private final HashCalculator myHashCalculator;


  public MirrorManagerImpl(@NotNull MirrorConfig config, @NotNull HashCalculator hash) {
    myHashCalculator = hash;
    myBaseMirrorsDir = config.getCachesDir();
    myMapFile = new File(myBaseMirrorsDir, "map");
    myInvalidDirsFile = new File(myBaseMirrorsDir, "invalid");
    loadInvalidDirs();
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


  public void invalidate(@NotNull final File dir) {
    synchronized (myLock) {
      List<String> urlsMappedToDir = getUrlsMappedToDir(dir);
      for (String url : urlsMappedToDir) {
        String dirName = myMirrorMap.remove(url);
        myInvalidDirNames.add(dirName);
      }
      saveMappingToFile();
      saveInvalidDirsToFile();
    }
  }


  @NotNull
  public Map<String, File> getMappings() {
    Map<String, String> mirrorMapSnapshot;
    synchronized (myLock) {
      mirrorMapSnapshot = new HashMap<String, String>(myMirrorMap);
    }
    Map<String, File> result = new HashMap<String, File>();
    for (Map.Entry<String, String> entry : mirrorMapSnapshot.entrySet()) {
      String url = entry.getKey();
      String dir = entry.getValue();
      result.put(url, new File(myBaseMirrorsDir, dir));
    }
    return result;
  }

  @Nullable
  @Override
  public String getUrl(@NotNull String cloneDirName) {
    Map<String, String> mirrorMapSnapshot;
    synchronized (myLock) {
      mirrorMapSnapshot = new HashMap<String, String>(myMirrorMap);
    }
    for (Map.Entry<String, String> e : mirrorMapSnapshot.entrySet()) {
      if (cloneDirName.equals(e.getValue()))
        return e.getKey();
    }
    return null;
  }

  public long getLastUsedTime(@NotNull final File dir) {
    File timestamp = new File(dir, "timestamp");
    if (timestamp.exists()) {
      try {
        List<String> lines = FileUtil.readFile(timestamp);
        if (lines.isEmpty())
          return dir.lastModified();
        else
          return Long.valueOf(lines.get(0));
      } catch (IOException e) {
        return dir.lastModified();
      }
    } else {
      return dir.lastModified();
    }
  }


  @NotNull
  private List<String> getUrlsMappedToDir(@NotNull final File dir) {
    synchronized (myLock) {
      List<String> urlsMappedToDir = new ArrayList<String>();
      for (Map.Entry<String, String> entry : myMirrorMap.entrySet()) {
        String url = entry.getKey();
        String dirName = entry.getValue();
        if (dir.equals(new File(myBaseMirrorsDir, dirName)))
          urlsMappedToDir.add(url);
      }
      return urlsMappedToDir;
    }
  }


  /**
   * Returns repository dir name for specified url. Every url gets unique dir name.
   * @param url url of interest
   * @return see above
   */
  @NotNull
  private String getDirNameForUrl(@NotNull final String url) {
    synchronized (myLock) {
      String dirName = myMirrorMap.get(url);
      if (dirName != null)
        return dirName;
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
      while (isOccupiedDirName(dirName) || isInvalidDirName(dirName)) {
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
    synchronized (myLock) {
      return myMirrorMap.values().contains(dirName)/* || new File(myBaseMirrorsDir, dirName).exists()*/;
    }
  }


  private boolean isInvalidDirName(@NotNull final String dirName) {
    synchronized (myLock) {
      return myInvalidDirNames.contains(dirName);
    }
  }


  private void saveMappingToFile() {
    synchronized (myLock) {
      LOG.debug("Save mapping to " + myMapFile.getAbsolutePath());
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> mirror : myMirrorMap.entrySet()) {
        String url = mirror.getKey();
        String dir = mirror.getValue();
        sb.append(url).append(" = ").append(dir).append("\n");
      }
      FileUtil.writeFile(myMapFile, sb.toString());
    }
  }


  private void saveInvalidDirsToFile() {
    synchronized (myLock) {
      LOG.debug("Save invalid dirs to " + myInvalidDirsFile.getAbsolutePath());
      StringBuilder sb = new StringBuilder();
      for (String dirName : myInvalidDirNames) {
        sb.append(dirName).append("\n");
      }
      FileUtil.writeFile(myInvalidDirsFile, sb.toString());
    }
  }


  private void loadInvalidDirs() {
    synchronized (myLock) {
      LOG.debug("Parse invalid dirs file " + myInvalidDirsFile.getAbsolutePath());
      if (myInvalidDirsFile.exists()) {
        for (String line : readLines(myInvalidDirsFile)) {
          String dirName = line.trim();
          if (dirName.length() > 0)
            myInvalidDirNames.add(dirName);
        }
      }
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
      for (String line : readLines(myMapFile)) {
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


  private List<String> readLines(@NotNull final File file) {
    synchronized (myLock) {
      try {
        return FileUtil.readFile(file);
      } catch (IOException e) {
        LOG.error("Error while reading file " + file.getAbsolutePath() + " assume it is empty", e);
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
            readMappings();
          }
        } catch (IOException e) {
          LOG.error("Cannot create a mapping file at " + myMapFile.getAbsolutePath() + ", start with empty mapping", e);
        }
      }
    }
  }


  private void restoreMapFile() {
    synchronized (myLock) {
      LOG.info("Restore mapping from existing repositories");
      Map<String, String> restoredMappings = restoreMappings();
      myMirrorMap.putAll(restoredMappings);
      saveMappingToFile();
    }
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
