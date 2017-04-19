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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public class GitBuildProgressLogger implements GitProgressLogger {

  private final BuildProgressLogger myLogger;
  private final AtomicInteger myBlockMessageCount = new AtomicInteger(0);
  private final AgentPluginConfig.GitProgressMode myProgressMode;

  public GitBuildProgressLogger(@NotNull BuildProgressLogger logger,
                                @NotNull AgentPluginConfig.GitProgressMode progressMode) {
    myLogger = logger;
    myProgressMode = progressMode;
  }

  public void openBlock(@NotNull String name) {
    myBlockMessageCount.set(0);
    if (myProgressMode == AgentPluginConfig.GitProgressMode.NONE) {
      //if progress should not be written to build log do not open block; write it
      //as a regular message instead, because teamcity doesn't show empty blocks
      //with no messages inside
      myLogger.message(name);
    } else {
      myLogger.activityStarted(name, "CUSTOM_GIT_PROGRESS");
    }
  }

  public void message(@NotNull String message) {
    myBlockMessageCount.incrementAndGet();
    myLogger.message(message);
  }

  @Override
  public void progressMessage(@NotNull String message) {
    myBlockMessageCount.incrementAndGet();
    switch (myProgressMode) {
      case NONE:
        return;
      case DEBUG:
        myLogger.logMessage(DefaultMessagesInfo.internalize(DefaultMessagesInfo.createTextMessage(message)));
        return;
      case NORMAL:
        myLogger.message(message);
    }
  }

  public void closeBlock(@NotNull String name) {
    //we need to close block only if progress was written to build log
    if (myProgressMode != AgentPluginConfig.GitProgressMode.NONE) {
      if (myBlockMessageCount.get() == 0)
        myLogger.message("");
      myLogger.activityFinished(name, "CUSTOM_GIT_PROGRESS");
    }
  }
}
