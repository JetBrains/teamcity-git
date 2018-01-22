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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitProgressLogger;
import org.jetbrains.annotations.NotNull;

public class GitProgressListener implements LineAwareByteArrayOutputStream.LineListener {
  private final GitProgressLogger myLogger;

  public GitProgressListener(@NotNull GitProgressLogger logger) {
    myLogger = logger;
  }

  public void newLineDetected(@NotNull String line) {
    myLogger.progressMessage(line);
  }
}
