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
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

public class Cleanup {

  private static final Logger LOG = Loggers.CLEANUP;
  private static final Pattern PATTERN_LOOSE_OBJECT = Pattern.compile("[0-9a-fA-F]{38}");
  private static final Semaphore ourSemaphore = new Semaphore(1);

  private final RepositoryManager myRepositoryManager;
  private final ServerPluginConfig myConfig;
  private final AtomicReference<RunGitError> myNativeGitError = new AtomicReference<>();

  public Cleanup(@NotNull final ServerPluginConfig config,
                 @NotNull final RepositoryManager repositoryManager) {
    myConfig = config;
    myRepositoryManager = repositoryManager;
  }

  public void run() {
    if (!ourSemaphore.tryAcquire()) {
      LOG.info("Skip git cleanup: another git cleanup process is running");
      return;
    }

    try {
      LOG.info("Git cleanup started");
      removeUnusedRepositories();
      cleanupMonitoringData();
      if (myConfig.isRunJGitGC()) {
        runJGitGC();
      } else if (myConfig.isRunNativeGC()) {
        runNativeGC();
      }
      LOG.info("Git cleanup finished");
    } finally {
      ourSemaphore.release();
    }
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
    boolean runInPlace = myConfig.runInPlaceGc();
    for (File gitDir : allDirs) {
      if (runInPlace) {
        synchronized (myRepositoryManager.getWriteLock(gitDir)) {
          runNativeGC(gitDir);
        }
      } else {
        runGcInCopy(gitDir);
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


  private void runGcInCopy(@NotNull File originalRepo) {
    Lock rmLock = myRepositoryManager.getRmLock(originalRepo).readLock();
    rmLock.lock();
    File gcRepo;
    try {
      if (!isGcNeeded(originalRepo))
        return;

      try {
        gcRepo = setupGcRepo(originalRepo);
      } catch (Exception e) {
        LOG.warnAndDebugDetails("Failed to create temporary repository for garbage collection, original repository: " + originalRepo.getAbsolutePath(), e);
        return;
      }

      try {
        repack(gcRepo);
        packRefs(gcRepo);
      } catch (Exception e) {
        LOG.warnAndDebugDetails("Error while running garbage collection in " + originalRepo.getAbsolutePath(), e);
        FileUtil.delete(gcRepo);
        return;
      }
    } finally {
      rmLock.unlock();
    }

    //remove alternates pointing to the original repo before swapping repositories
    FileUtil.delete(new File(gcRepo, "objects/info/alternates"));

    File oldDir;
    try {
      oldDir = createTempDir(originalRepo.getParentFile(), originalRepo.getName() + ".old");
      FileUtil.delete(oldDir);
    } catch (Exception e) {
      LOG.warnAndDebugDetails("Error while creating temporary directory for " + originalRepo.getAbsolutePath(), e);
      FileUtil.delete(gcRepo);
      return;
    }

    //swap repositories with write rm lock which guarantees no one uses the original repository
    Lock rmWriteLock = myRepositoryManager.getRmLock(originalRepo).writeLock();
    rmWriteLock.lock();
    try {
      if (!originalRepo.renameTo(oldDir)) {
        LOG.warn("Failed to rename " + originalRepo.getName() + " to " + oldDir.getName());
        return;
      }
      if (!gcRepo.renameTo(originalRepo)) {
        LOG.warn("Failed to rename " + gcRepo.getName() + " to " + originalRepo.getName() + ", will try restoring old repository");
        if (!oldDir.renameTo(originalRepo)) {
          LOG.warn("Failed to rename " + oldDir.getName() + " to " + originalRepo.getName());
        }
      }
    } finally {
      rmWriteLock.unlock();
      FileUtil.delete(oldDir);
      FileUtil.delete(gcRepo);
    }
  }

  private void repack(final File gcRepo) throws VcsException {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(gcRepo);
    cmd.setExePath(myConfig.getPathToGit());
    cmd.addParameter("repack");
    cmd.addParameters("-a", "-d", "-f");
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null, new SimpleCommandLineProcessRunner.RunCommandEventsAdapter() {
      @Override
      public Integer getOutputIdleSecondsTimeout() {
        return myConfig.getRepackIdleTimeoutSeconds();
      }
    });
    VcsException commandError = CommandLineUtil.getCommandLineError("git repack", result);
    if (commandError != null) {
      LOG.warnAndDebugDetails("Error while running 'git repack' in " + gcRepo.getAbsolutePath(), commandError);
      throw commandError;
    }
  }

  private void packRefs(@NotNull File gcRepo) throws VcsException {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(gcRepo);
    cmd.setExePath(myConfig.getPathToGit());
    cmd.addParameter("pack-refs");
    cmd.addParameters("--all");
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null, new SimpleCommandLineProcessRunner.RunCommandEventsAdapter() {
      @Override
      public Integer getOutputIdleSecondsTimeout() {
        return myConfig.getPackRefsIdleTimeoutSeconds();
      }
    });
    VcsException commandError = CommandLineUtil.getCommandLineError("git pack-refs", result);
    if (commandError != null) {
      LOG.warnAndDebugDetails("Error while running 'git pack-refs' in " + gcRepo.getAbsolutePath(), commandError);
      throw commandError;
    }
  }

  private boolean isGcNeeded(@NotNull File gitDir) {
    try {
      //implement logic from git gc --auto, jgit version we use doesn't have it yet
      //and native git doesn't provide a dedicated command for that
      FileRepository db = (FileRepository) new RepositoryBuilder().setBare().setGitDir(gitDir).build();
      return tooManyPacks(db) || tooManyLooseObjects(db);
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Error while checking if garbage collection is needed in " + gitDir.getAbsolutePath(), e);
      return false;
    }
  }

  private boolean tooManyPacks(@NotNull FileRepository repo) {
    int limit = repo.getConfig().getInt("gc", "autopacklimit", 50);
    if (limit <= 0)
      return false;
    int packCount = 0;
    for (PackFile packFile : repo.getObjectDatabase().getPacks()) {
      if (!packFile.shouldBeKept())
        packCount++;
      if (packCount > limit)
        return true;
    }
    return false;
  }


  private boolean tooManyLooseObjects(@NotNull FileRepository repo) {
    int limit = repo.getConfig().getInt("gc", "auto", 6700);
    if (limit <= 0)
      return false;
    //SHA is evenly distributed, we can estimate number of loose object by counting them in a single bucket (from jgit internals)
    int bucketLimit = (limit + 255) / 256;
    File bucket = new File(repo.getObjectsDirectory(), "17");
    if (!bucket.isDirectory())
      return false;
    String[] files = bucket.list();
    if (files == null)
      return false;
    int count = 0;
    for (String fileName : files) {
      if (PATTERN_LOOSE_OBJECT.matcher(fileName).matches())
        count++;
      if (count > bucketLimit)
        return true;
    }
    return false;
  }


  @NotNull
  private File setupGcRepo(@NotNull File gitDir) throws IOException {
    File result = createTempDir(gitDir.getParentFile(), gitDir.getName() + ".gc");
    Repository repo = new RepositoryBuilder().setBare().setGitDir(result).build();
    try {
      repo.create(true);
    } finally {
      repo.close();
    }

    //setup alternates, 'git repack' in a repo with alternates creates a pack
    //in this repo without affecting the repo alternates point to
    File objectsDir = new File(result, "objects");
    File objectsInfo = new File(objectsDir, "info");
    objectsInfo.mkdirs();
    FileUtil.writeFileAndReportErrors(new File(objectsInfo, "alternates"), new File(gitDir, "objects").getCanonicalPath());

    copyIfExist(new File(gitDir, "packed-refs"), result);
    copyIfExist(new File(gitDir, "timestamp"), result);
    copyDirIfExist(new File(gitDir, "refs"), result);
    copyDirIfExist(new File(gitDir, "monitoring"), result);
    return result;
  }

  private void copyIfExist(@NotNull File srcFile, @NotNull File dstDir) throws IOException {
    if (srcFile.exists())
      FileUtil.copy(srcFile, new File(dstDir, srcFile.getName()));
  }

  private void copyDirIfExist(@NotNull File srcDir, @NotNull File dstDir) throws IOException {
    if (srcDir.exists())
      FileUtil.copyDir(srcDir, new File(dstDir, srcDir.getName()));
  }

  @NotNull
  private File createTempDir(@NotNull final File parentDir, @NotNull String name) throws IOException {
    File dir = new File(parentDir, name);
    if (dir.mkdir())
      return dir;

    int suffix = 0;
    while (true) {
      suffix++;
      String tmpDirName = name + suffix;
      dir = new File(parentDir, tmpDirName);
      if (dir.mkdir())
        return dir;
    }
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
      myNativeGitError.set(new RunGitError(pathToGit, commandError));
      LOG.info("Cannot run native git", commandError);
      return false;
    } else {
      myNativeGitError.set(null);
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
          LOG.info("Finish 'git --git-dir=" + bareGitDir.getAbsolutePath() + " gc', duration: " + (finish - start) + "ms");
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


  @Nullable
  public RunGitError getNativeGitError() {
    return myNativeGitError.get();
  }

  public static class RunGitError extends Pair<String, VcsException> {
    public RunGitError(@NotNull String gitPath, @NotNull VcsException error) {
      super(gitPath, error);
    }

    @NotNull
    public String getGitPath() {
      return first;
    }

    @NotNull
    public VcsException getError() {
      return second;
    }
  }
}
