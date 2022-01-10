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

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import org.jetbrains.annotations.Nullable;

public class ProcessTimeoutCallback extends SimpleCommandLineProcessRunner.ProcessRunCallbackAdapter {

  @Nullable private final Integer myTimeoutSeconds;
  @Nullable private final Integer myMaxAcceptedOutputSize;

  public ProcessTimeoutCallback(int timeoutSeconds) {
    this(timeoutSeconds, null);
  }

  public ProcessTimeoutCallback(@Nullable final Integer timeoutSeconds, @Nullable final Integer maxAcceptedOutputSize) {
    myTimeoutSeconds = timeoutSeconds;
    myMaxAcceptedOutputSize = maxAcceptedOutputSize;
  }

  @Nullable
  @Override
  public Integer getOutputIdleSecondsTimeout() {
    return myTimeoutSeconds;
  }

  @Nullable
  @Override
  public Integer getMaxAcceptedOutputSize() {
    return myMaxAcceptedOutputSize;
  }

  @Override
  public boolean isUseProcessTreeTerminator() {
    return true;
  }
}
