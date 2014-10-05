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

import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class FetchSettings {
  private final AuthSettings myAuthSettings;
  private final GitProgress myProgress;

  public FetchSettings(@NotNull AuthSettings authSettings) {
    this(authSettings, GitProgress.NO_OP);
  }

  public FetchSettings(@NotNull AuthSettings authSettings, @NotNull GitProgress progress) {
    myAuthSettings = authSettings;
    myProgress = progress;
  }

  @NotNull
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  @NotNull
  public ByteArrayOutputStream createStdoutBuffer() {
    ByteArrayOutputStream stdoutBuffer;
    if (myProgress != GitProgress.NO_OP) {
      stdoutBuffer = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new JGitProgressParser(myProgress));
    } else {
      stdoutBuffer = new ByteArrayOutputStream();
    }
    return stdoutBuffer;
  }

  @NotNull
  public GitProgress getProgress() {
    return myProgress;
  }
}
