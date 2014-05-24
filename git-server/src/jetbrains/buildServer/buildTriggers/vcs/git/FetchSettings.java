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
import jetbrains.buildServer.vcs.FetchService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class FetchSettings {
  private final AuthSettings myAuthSettings;
  private FetchService.FetchRepositoryCallback myProgressCallback;

  public FetchSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
  }

  @NotNull
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  public void setProgressCallback(@Nullable FetchService.FetchRepositoryCallback progressCallback) {
    myProgressCallback = progressCallback;
  }

  @NotNull
  public ByteArrayOutputStream createStdoutBuffer() {
    ByteArrayOutputStream stdoutBuffer;
    if (myProgressCallback != null) {
      stdoutBuffer = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new JGitProgressParser(myProgressCallback));
    } else {
      stdoutBuffer = new ByteArrayOutputStream();
    }
    return stdoutBuffer;
  }
}
