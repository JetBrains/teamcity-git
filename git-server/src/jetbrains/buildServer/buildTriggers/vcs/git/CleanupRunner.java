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

import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import org.jetbrains.annotations.NotNull;
import org.quartz.CronExpression;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class CleanupRunner {

  private final ServerPluginConfig myConfig;
  private final ScheduledExecutorService myExecutor;
  private final Cleanup myCleanup;
  private final AtomicReference<String> myCron = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> myCleanupFeature = new AtomicReference<>();
  //lock is used to have only one scheduled clean-up task
  private final ReentrantLock myLock = new ReentrantLock();

  public CleanupRunner(@NotNull final ExecutorServices executor,
                       @NotNull final ServerPluginConfig config,
                       @NotNull final Cleanup cleanup) {
    myExecutor = executor.getNormalExecutorService();
    myConfig = config;
    myCleanup = cleanup;
    myExecutor.scheduleAtFixedRate(this::updateSchedule, 10, 10, TimeUnit.MINUTES);
  }


  /**
   * Schedules a new cleanup task if cron expression was changed
   */
  private void updateSchedule() {
    if (!myLock.tryLock())
      return;
    try {
      CronExpression cron = myConfig.getCleanupCronExpression();
      String cronStr = cron != null ? cron.getCronExpression() : null;
      if (Objects.equals(myCron.get(), cronStr))
        return;

      ScheduledFuture<?> feature = myCleanupFeature.get();
      if (feature != null) {
        feature.cancel(false);
      }

      if (cron != null) {
        schedule(cron);
      } else {
        myCleanupFeature.set(null);
        myCron.set(null);
      }
    } finally {
      myLock.unlock();
    }
  }


  /**
   * Runs cleanup and schedules itself
   */
  private void runCleanup() {
    myCleanup.run();

    myLock.lock();
    try {
      CronExpression cron = myConfig.getCleanupCronExpression();
      //schedule next clean-up task only if cron expression wasn't changed,
      //changed cron handled by updateSchedule task
      if (cron != null && Objects.equals(myCron.get(), cron.getCronExpression())) {
        schedule(cron);
      } else {
        //reset future & cron to avoid ABA problem in updateSchedule(): cron is A,
        //updateSchedule() remembers it, cron becomes B, runCleanup() doesn't schedule,
        //cron is changed back to A, updateSchedule() also doesn't schedule
        myCleanupFeature.set(null);
        myCron.set(null);
      }
    } finally {
      myLock.unlock();
    }
  }


  private void schedule(@NotNull CronExpression cron) {
    Date now = new Date();
    Date next = cron.getNextValidTimeAfter(now);
    ScheduledFuture<?> future = myExecutor.schedule(this::runCleanup, next.getTime() - now.getTime(), TimeUnit.MILLISECONDS);
    myCleanupFeature.set(future);
    myCron.set(cron.getCronExpression());
  }
}
