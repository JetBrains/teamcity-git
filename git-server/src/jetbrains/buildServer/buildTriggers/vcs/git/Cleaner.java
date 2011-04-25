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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Cleans unused git repositories
 * @author dmitry.neverov
 */
public class Cleaner extends BuildServerAdapter {

  private static Logger LOG = Loggers.CLEANUP;

  private final SBuildServer myServer;
  private final GitVcsSupport myGitVcsSupport;
  private final PluginConfig myConfig;

  public Cleaner(@NotNull final SBuildServer server,
                 @NotNull final EventDispatcher<BuildServerListener> dispatcher,
                 @NotNull final PluginConfig config,
                 @NotNull final GitVcsSupport gitSupport) {
    myServer = server;
    myConfig = config;
    myGitVcsSupport = gitSupport;
    dispatcher.addListener(this);
  }

  @Override
  public void cleanupStarted() {
    super.cleanupFinished();
    myServer.getExecutor().submit(new Runnable() {
      public void run() {
        clean();
      }
    });
  }

  private void clean() {
    LOG.debug("Clean started");
    removeUnusedRepositories();
    if (myConfig.isRunNativeGC()) {
      runNativeGC();
    }
    LOG.debug("Clean finished");
  }

  private void removeUnusedRepositories() {
    Collection<? extends SVcsRoot> gitRoots = getAllGitRoots();
    List<File> unusedDirs = getUnusedDirs(gitRoots);
    LOG.debug("Remove unused repositories started");
    for (File dir : unusedDirs) {
      LOG.info("Remove unused dir " + dir.getAbsolutePath());
      Lock rmLock = myGitVcsSupport.getRmLock(dir).writeLock();
      rmLock.lock();
      try {
        FileUtil.delete(dir);
      } finally {
        rmLock.unlock();
      }
      LOG.debug("Remove unused dir " + dir.getAbsolutePath() + " finished");
    }
    LOG.debug("Remove unused repositories finished");
  }

  private Collection<? extends SVcsRoot> getAllGitRoots() {
    return myServer.getVcsManager().findRootsByVcsName(Constants.VCS_NAME);
  }

  private List<File> getUnusedDirs(Collection<? extends SVcsRoot> roots) {
    List<File> repositoryDirs = getAllRepositoryDirs();
    for (VcsRoot root : roots) {
      try {
        File usedRootDir = Settings.getRepositoryDir(myGitVcsSupport.getCachesDir(), root);
        repositoryDirs.remove(usedRootDir);
      } catch (Exception e) {
        LOG.warn("Get repository path error", e);
      }
    }
    return repositoryDirs;
  }

  private List<File> getAllRepositoryDirs() {
    return new ArrayList<File>(FileUtil.getSubDirectories(myGitVcsSupport.getCachesDir()));
  }

  private long minutes2Milliseconds(int quotaInMinutes) {
    return quotaInMinutes * 60 * 1000L;
  }

  private void runNativeGC() {
    final long start = System.currentTimeMillis();
    final long gcTimeQuota = minutes2Milliseconds(myConfig.getNativeGCQuotaMinutes());
    LOG.info("Garbage collection started");
    List<File> allDirs = getAllRepositoryDirs();
    int runGCCounter = 0;
    for (File gitDir : allDirs) {
      synchronized (myGitVcsSupport.getWriteLock(gitDir)) {
        runNativeGC(gitDir);
      }
      runGCCounter++;
      final long repositoryFinish = System.currentTimeMillis();
      if ((repositoryFinish - start) > gcTimeQuota) {
        final int restRepositories = allDirs.size() - runGCCounter;
        if (restRepositories > 0) {
          LOG.info("Garbage collection quota exceeded, skip " + restRepositories + " repositories");
          break;
        }
      }
    }
    final long finish = System.currentTimeMillis();
    LOG.info("Garbage collection finished, it took " + (finish - start) + "ms");
  }

  private void runNativeGC(final File bareGitDir) {
    String pathToGit = myConfig.getPathToGit();
    try {
      final long start = System.currentTimeMillis();
      GeneralCommandLine cl = new GeneralCommandLine();
      cl.setWorkingDirectory(bareGitDir.getParentFile());
      cl.setExePath(pathToGit);
      cl.addParameter("--git-dir="+bareGitDir.getCanonicalPath());
      cl.addParameter("gc");
      cl.addParameter("--auto");
      cl.addParameter("--quiet");

      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, null, new SimpleCommandLineProcessRunner.RunCommandEvents() {
        public void onProcessStarted(Process ps) {
          if (LOG.isDebugEnabled()) {
            LOG.info("Start 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'");
          }
        }
        public void onProcessFinished(Process ps) {
          if (LOG.isDebugEnabled()) {
            final long finish = System.currentTimeMillis();
            LOG.info("Finish 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc', it took " + (finish - start) + "ms");
          }
        }
        public Integer getOutputIdleSecondsTimeout() {
          return 3 * 60 * 60;//3 hours
        }
      });

      VcsException commandError = CommandLineUtil.getCommandLineError("'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'", result);
      if (commandError != null) {
        LOG.error("Error while running 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'", commandError);
      }
      if (result.getStderr().length() > 0) {
        LOG.debug("Output produced by 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'");
        LOG.debug(result.getStderr());
      }
    } catch (Exception e) {
      LOG.error("Error while running 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'", e);
    }
  }
}
