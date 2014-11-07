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
import org.jetbrains.annotations.NotNull;

public class GitBuildProgressLogger implements GitProgressLogger {

  private final BuildProgressLogger myLogger;

  public GitBuildProgressLogger(@NotNull BuildProgressLogger logger) {
    myLogger = logger;
  }

  public void openBlock(@NotNull String name) {
    myLogger.activityStarted(name, "CUSTOM_GIT_PROGRESS");
  }

  public void message(@NotNull String message) {
    myLogger.message(message);
  }

  public void closeBlock(@NotNull String name) {
    myLogger.activityFinished(name, "CUSTOM_GIT_PROGRESS");
  }
}
