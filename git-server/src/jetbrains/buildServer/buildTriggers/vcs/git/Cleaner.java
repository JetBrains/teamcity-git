/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cleans unused git repositories
 * @author dmitry.neverov
 */
public class Cleaner extends BuildServerAdapter {

  private static Logger LOG = Logger.getInstance(Cleaner.class.getName());

  private final SBuildServer myServer;
  private final ServerPaths myPaths;
  private final GitVcsSupport myGitVcsSupport;

  public Cleaner(@NotNull final SBuildServer server,
                 @NotNull final EventDispatcher<BuildServerListener> dispatcher,
                 @NotNull final ServerPaths paths,
                 @NotNull final GitVcsSupport gitSupport) {
    myServer = server;
    myPaths = paths;
    myGitVcsSupport = gitSupport;
    dispatcher.addListener(this);
  }

  @Override
  public void cleanupFinished() {
    super.cleanupFinished();
    myServer.getExecutor().submit(new Runnable() {
      public void run() {
        clean();
      }
    });
  }

  private void clean() {
    Collection<? extends SVcsRoot> gitRoots = getAllGitRoots();
    List<File> unusedDirs = getUnusedDirs(gitRoots);
    for (File dir : unusedDirs) {
      LOG.info("Remove unused dir " + dir.getAbsolutePath());
      synchronized (myGitVcsSupport.getRepositoryLock(dir)) {
        FileUtil.delete(dir);
      }
    }
  }

  private Collection<? extends SVcsRoot> getAllGitRoots() {
    return myServer.getVcsManager().findRootsByVcsName(Constants.VCS_NAME);
  }

  private List<File> getUnusedDirs(Collection<? extends SVcsRoot> roots) {
    List<File> repositoryDirs = getAllRepositoryDirs();
    File cacheDir = new File(myPaths.getCachesDir());
    for (VcsRoot root : roots) {
      try {
        File usedRootDir = Settings.getRepositoryPath(cacheDir, root);
        repositoryDirs.remove(usedRootDir);
      } catch (Exception e) {
        LOG.warn("Get repository path error", e);
      }
    }
    return repositoryDirs;
  }

  private List<File> getAllRepositoryDirs() {
    String teamcityCachesPath = myPaths.getCachesDir();
    File gitCacheDir = new File(teamcityCachesPath, "git");
    return new ArrayList<File>(FileUtil.getSubDirectories(gitCacheDir));
  }
}
