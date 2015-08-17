/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class Cleanup {

  private static Logger LOG = Loggers.CLEANUP;

  private final RepositoryManager myRepositoryManager;
  private final ServerPluginConfig myConfig;

  public Cleanup(@NotNull final ServerPluginConfig config,
                 @NotNull final RepositoryManager repositoryManager) {
    myConfig = config;
    myRepositoryManager = repositoryManager;
  }

  public void run() {
    LOG.info("Git cleanup started");
    removeUnusedRepositories();
    cleanupMonitoringData();
    if (myConfig.isRunJGitGC()) {
      runJGitGC();
    } else if (myConfig.isRunNativeGC()) {
      runNativeGC();
    }
    LOG.info("Git cleanup finished");
  }

  private void removeUnusedRepositories() {
    List<File> unusedDirs = getUnusedDirs();
    LOG.debug("Remove unused git repository clones started");
    for (File dir : unusedDirs) {
      LOG.info("Remove unused git repository dir " + dir.getAbsolutePath());
      Lock rmLock = myRepositoryManager.getRmLock(dir).writeLock();
      rmLock.lock();
      boolean deleted = false;
      try {
        deleted = FileUtil.delete(dir);
      } finally {
        rmLock.unlock();
      }
      if (deleted) {
        LOG.debug("Remove unused git repository dir " + dir.getAbsolutePath() + " finished");
      } else {
        LOG.error("Cannot delete unused git repository dir " + dir.getAbsolutePath());
        myRepositoryManager.invalidate(dir);
      }
    }
    LOG.debug("Remove unused git repository clones finished");
  }

  @NotNull
  private List<File> getUnusedDirs() {
    return myRepositoryManager.getExpiredDirs();
  }

  private List<File> getAllRepositoryDirs() {
    return new ArrayList<File>(FileUtil.getSubDirectories(myRepositoryManager.getBaseMirrorsDir()));
  }

  private long minutes2Milliseconds(int quotaInMinutes) {
    return quotaInMinutes * 60 * 1000L;
  }

  private void cleanupMonitoringData() {
    LOG.debug("Start cleaning git monitoring data");
    for (File repository : getAllRepositoryDirs()) {
      File monitoring = new File(repository, myConfig.getMonitoringDirName());
      File[] files = monitoring.listFiles();
      if (files != null) {
        for (File monitoringData : files) {
          if (isExpired(monitoringData)) {
            LOG.debug("Remove old git monitoring data " + monitoringData.getAbsolutePath());
            FileUtil.delete(monitoringData);
          }
        }
      }
    }
    LOG.debug("Finish cleaning git monitoring data");
  }

  private boolean isExpired(@NotNull File f) {
    long age = System.currentTimeMillis() - f.lastModified();
    long ageHours = age / Dates.ONE_HOUR;
    return ageHours > myConfig.getMonitoringExpirationTimeoutHours();
  }

  private void runNativeGC() {
    if (!isNativeGitInstalled()) {
      LOG.info("Cannot find native git, skip running git gc");
      return;
    }
    final long startNanos = System.nanoTime();
    final long gcTimeQuotaNanos = TimeUnit.MINUTES.toNanos(myConfig.getNativeGCQuotaMinutes());
    LOG.info("Git garbage collection started");
    List<File> allDirs = getAllRepositoryDirs();
    Collections.shuffle(allDirs);
    int runGCCounter = 0;
    for (File gitDir : allDirs) {
      synchronized (myRepositoryManager.getWriteLock(gitDir)) {
        runNativeGC(gitDir);
      }
      runGCCounter++;
      final long repositoryFinishNanos = System.nanoTime();
      if ((repositoryFinishNanos - startNanos) > gcTimeQuotaNanos) {
        final int restRepositories = allDirs.size() - runGCCounter;
        if (restRepositories > 0) {
          LOG.info("Git garbage collection quota exceeded, skip " + restRepositories + " repositories");
          break;
        }
      }
    }
    final long finishNanos = System.nanoTime();
    LOG.info("Git garbage collection finished, it took " + TimeUnit.NANOSECONDS.toMillis(finishNanos - startNanos) + "ms");
  }

  private void runJGitGC() {
    final long startNanos = System.nanoTime();
    final long gcTimeQuotaNanos = TimeUnit.MINUTES.toNanos(myConfig.getNativeGCQuotaMinutes());
    LOG.info("Git garbage collection started");
    List<File> allDirs = getAllRepositoryDirs();
    Collections.shuffle(allDirs);
    int runGCCounter = 0;
    Boolean nativeGitInstalled = null;
    boolean enableNativeGitLogged = false;
    for (File gitDir : allDirs) {
      synchronized (myRepositoryManager.getWriteLock(gitDir)) {
        try {
          LOG.info("Start garbage collection in " + gitDir.getAbsolutePath());
          long repositoryStartNanos = System.nanoTime();
          runJGitGC(gitDir);
          LOG.info("Garbage collection finished in " + gitDir.getAbsolutePath() + ", duration: " +
                   TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - repositoryStartNanos) + "ms");
        } catch (Exception e) {
          LOG.warnAndDebugDetails("Error while running garbage collection in " + gitDir.getAbsolutePath(), e);
          if ((System.nanoTime() - startNanos) < gcTimeQuotaNanos) { //if quota is not exceeded try running a native git
            if (nativeGitInstalled == null) {
              LOG.info("Check if native git is installed");
              nativeGitInstalled = isNativeGitInstalled();
            }
            if (nativeGitInstalled) {
              runNativeGC(gitDir);
            } else {
              if (!enableNativeGitLogged) {
                LOG.info("Cannot find a native git, please install it and provide a path to git in the 'teamcity.server.git.executable.path' internal property.");
                enableNativeGitLogged = true;
              }
            }
          }
        }
      }
      runGCCounter++;
      final long repositoryFinishNanos = System.nanoTime();
      if ((repositoryFinishNanos - startNanos) > gcTimeQuotaNanos) {
        final int restRepositories = allDirs.size() - runGCCounter;
        if (restRepositories > 0) {
          LOG.info("Git garbage collection quota exceeded, skip " + restRepositories + " repositories");
          break;
        }
      }
    }
    final long finishNanos = System.nanoTime();
    LOG.info("Git garbage collection finished, it took " + TimeUnit.NANOSECONDS.toMillis(finishNanos - startNanos) + "ms");
  }

  private boolean isNativeGitInstalled() {
    String pathToGit = myConfig.getPathToGit();
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(myRepositoryManager.getBaseMirrorsDir());
    cmd.setExePath(pathToGit);
    cmd.addParameter("version");
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    VcsException commandError = CommandLineUtil.getCommandLineError("git version", result);
    if (commandError != null) {
      LOG.info("Cannot run native git", commandError);
      return false;
    }
    return true;
  }

  private void runJGitGC(final File bareGitDir) throws IOException, VcsException {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(bareGitDir);
    cmd.setExePath(myConfig.getFetchProcessJavaPath());
    cmd.addParameters("-Xmx" + myConfig.getGcProcessMaxMemory(),
                      "-cp", myConfig.getFetchClasspath(),
                      GitGcProcess.class.getName(),
                      bareGitDir.getCanonicalPath());
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null, new SimpleCommandLineProcessRunner.RunCommandEventsAdapter() {
      @Nullable
      @Override
      public Integer getOutputIdleSecondsTimeout() {
        return 60 * myConfig.getNativeGCQuotaMinutes();
      }
    });
    VcsException commandError = CommandLineUtil.getCommandLineError("git gc", result, false, true);
    if (commandError != null)
      throw commandError;
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

      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, null, new SimpleCommandLineProcessRunner.ProcessRunCallback() {
        public void onProcessStarted(Process ps) {
          LOG.info("Start 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc'");
        }
        public void onProcessFinished(Process ps) {
          final long finish = System.currentTimeMillis();
          LOG.info("Finish 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc', it took " + (finish - start) + "ms");
        }
        public Integer getOutputIdleSecondsTimeout() {
          return 60 * myConfig.getNativeGCQuotaMinutes();
        }
        public Integer getMaxAcceptedOutputSize() {
          return null;
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
