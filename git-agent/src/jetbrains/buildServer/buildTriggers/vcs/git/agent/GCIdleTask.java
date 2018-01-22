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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.NamedThreadFactory;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs 'git gc' when agent is idle
 */
public class GCIdleTask implements AgentIdleTasks.Task {

  private final BuildAgentConfiguration myAgentConfig;
  private final MirrorManager myMirrorManager;
  //nano timestamp of the last build or of the agent start, used to delay git gc
  private final AtomicLong myBuildFinishTime = new AtomicLong(-1);
  //name of the mirror dir -> nano timestamp of the previous git gc
  private final ConcurrentMap<String, Long> myGcTimestamp = new ConcurrentHashMap<String, Long>();
  //ref containing the thread executing 'git gc' or null if 'git gc' is not running
  private final AtomicReference<Thread> myGcThread = new AtomicReference<Thread>();

  public GCIdleTask(@NotNull EventDispatcher<AgentLifeCycleListener> events,
                    @NotNull AgentIdleTasks idleTasks,
                    @NotNull BuildAgentConfiguration agentConfig,
                    @NotNull MirrorManager mirrorManager) {
    myAgentConfig = agentConfig;
    myMirrorManager = mirrorManager;
    events.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void buildFinished(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
        myBuildFinishTime.set(System.nanoTime());
      }
      @Override
      public void agentStarted(@NotNull BuildAgent agent) {
        myBuildFinishTime.set(System.nanoTime());
      }

      @Override
      public void buildStarted(@NotNull AgentRunningBuild runningBuild) {
        Thread thread = myGcThread.get();
        if (thread != null)
          thread.interrupt();
      }
    });
    idleTasks.addRecurringTask(this);
  }


  @NotNull
  @Override
  public String getName() {
    return "git gc";
  }


  @Override
  public void execute(@NotNull InterruptState interruptState) {
    if (!isEnabled())
      return;
    if (!isDelayPassed())
      return;
    long t0 = System.currentTimeMillis();
    try {
      myGcThread.set(Thread.currentThread());
      Loggers.VCS.debug("Start git gc");
      runGc(interruptState);
    } catch (Exception e) {
      Loggers.VCS.debug("Finished git gc in " + (System.currentTimeMillis() - t0) + "ms");
    } finally {
      myGcThread.set(null);
    }
  }


  private void runGc(@NotNull InterruptState interruptState) {
    if (interruptState.isInterrupted())
      return;
    List<File> mirrors = listMirrors();
    Collections.shuffle(mirrors);
    for (File mirror : mirrors) {
      if (interruptState.isInterrupted())
        return;
      Long previousGC = myGcTimestamp.get(mirror.getName());
      if (previousGC != null && TimeUnit.NANOSECONDS.toHours(System.nanoTime() - previousGC) < getDelaySinceLastGCHours())
        continue;
      long t0 = System.currentTimeMillis();
      String path = mirror.getAbsolutePath();
      Loggers.VCS.debug("Run git gc in " + path);
      Disposable name = NamedThreadFactory.patchThreadName("Run git gc in " + path);
      try {
        new NativeGitFacade("git", GitProgressLogger.NO_OP, mirror).gc().call();
        myGcTimestamp.put(mirror.getName(), System.nanoTime());
      } catch (Exception e) {
        Loggers.VCS.warnAndDebugDetails("Error while running git gc in " + path, e);
      } finally {
        name.dispose();
        Loggers.VCS.debug("Finished git gc in " + path + " in " + (System.currentTimeMillis() - t0) + "ms");
      }
    }
  }


  @NotNull
  private List<File> listMirrors() {
    File mirrorsDir = myMirrorManager.getBaseMirrorsDir();
    File[] mirrors = mirrorsDir.listFiles();
    if (mirrors == null)
      return Collections.emptyList();
    List<File> result = new ArrayList<File>();
    for (File f : mirrors) {
      if (isGitRepo(f))
        result.add(f);
    }
    return result;
  }


  private boolean isGitRepo(@NotNull File gitDir) {
    try {
      new RepositoryBuilder().setGitDir(gitDir).setMustExist(true).build();
      return true;
    } catch (IOException e) {
      return false;
    }
  }


  private boolean isEnabled() {
    return "true".equals(myAgentConfig.getConfigurationParameters().get("teamcity.git.idleGcEnabled"));
  }


  private boolean isDelayPassed() {
    long buildFinishTime = myBuildFinishTime.get();
    if (buildFinishTime == -1)
      return false;
    return TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - buildFinishTime) >= getDelaySinceLastBuildMinutes();
  }


  private long getDelaySinceLastBuildMinutes() {
    return getLongParameter("teamcity.git.idleGcDelayMinutes", 30);
  }


  private long getDelaySinceLastGCHours() {
    return getLongParameter("teamcity.git.idleGcRateHours", 12);
  }


  private long getLongParameter(@NotNull String name, long defaultValue) {
    String value = myAgentConfig.getConfigurationParameters().get(name);
    if (value == null) {
      return defaultValue;
    } else {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
  }
}
