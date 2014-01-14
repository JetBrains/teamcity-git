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

import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import org.jetbrains.annotations.NotNull;
import org.quartz.CronExpression;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupRunner implements Runnable {

  private final RepositoryManager myRepositoryManager;
  private final ServerPluginConfig myConfig;
  private final ScheduledExecutorService myExecutor;
  private volatile boolean myFirstRun = true;

  public CleanupRunner(@NotNull final ExecutorServices executor,
                       @NotNull final ServerPluginConfig config,
                       @NotNull final RepositoryManager repositoryManager) {
    myExecutor = executor.getNormalExecutorService();
    myConfig = config;
    myRepositoryManager = repositoryManager;
    myExecutor.submit(this);
  }

  public void run() {
    if (myFirstRun) {
      myFirstRun = false;
    } else {
      new Cleanup(myConfig, myRepositoryManager).run();
    }
    schedule();
  }

  private void schedule() {
    CronExpression cron = myConfig.getCleanupCronExpression();
    if (cron != null) {
      Date now = new Date();
      Date next = cron.getNextValidTimeAfter(now);
      myExecutor.schedule(this, next.getTime() - now.getTime(), TimeUnit.MILLISECONDS);
    } else {
      //schedule ourselves to check if the cron expression specified
      myExecutor.schedule(this, 10, TimeUnit.MINUTES);
      //do not run cleanup next time, just schedule
      myFirstRun = true;
    }
  }
}
