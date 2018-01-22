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

import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
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
  public ProgressMonitor createProgressMonitor() {
    if (myProgress == GitProgress.NO_OP)
      return NullProgressMonitor.INSTANCE;
    Writer w = new Writer() {
      @Override
      public void write(final String str) throws IOException {
        myProgress.reportProgress(str.trim());
      }
      @Override public void write(final char[] cbuf, final int off, final int len) throws IOException {}
      @Override public void flush() throws IOException {}
      @Override public void close() throws IOException {}
    };

    return new TextProgressMonitor(w);
  }

  @NotNull
  public GitProgress getProgress() {
    return myProgress;
  }
}
