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

import java.io.ByteArrayOutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

public class FetchSettings {
  private final AuthSettings myAuthSettings;
  private final GitProgress myProgress;
  private final Collection<RefSpec> myRefSpecs;
  private FetchMode myFetchMode;

  public FetchSettings(@NotNull AuthSettings authSettings) {
    this(authSettings, Collections.emptyList());
  }

  public FetchSettings(@NotNull AuthSettings authSettings, @NotNull Collection<RefSpec> refSpecs) {
    this(authSettings, GitProgress.NO_OP, refSpecs);
  }

  public FetchSettings(@NotNull AuthSettings authSettings, @NotNull GitProgress progress) {
    this(authSettings, progress, Collections.emptyList());
  }

  public FetchSettings(@NotNull AuthSettings authSettings, @NotNull GitProgress progress, @NotNull Collection<RefSpec> refSpecs) {
    myAuthSettings = authSettings;
    myProgress = progress;
    myRefSpecs = refSpecs;
    myFetchMode = FetchMode.FETCH_REF_SPECS;
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
      public void write(final String str) {
        myProgress.reportProgress(str.trim());
      }
      @Override public void write(@NotNull final char[] cbuf, final int off, final int len) {}
      @Override public void flush() {}
      @Override public void close() {}
    };

    return new TextProgressMonitor(w);
  }

  @NotNull
  public GitProgress getProgress() {
    return myProgress;
  }

  @NotNull
  public static FetchMode getFetchMode(boolean fetchAllRefs, boolean includeTags) {
    if (fetchAllRefs && includeTags) return FetchMode.FETCH_ALL_REFS;
    if (fetchAllRefs) return FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS;
    return FetchMode.FETCH_REF_SPECS;
  }

  @NotNull
  public Collection<RefSpec> getRefSpecs() {
    return myRefSpecs;
  }

  @NotNull
  public FetchMode getFetchMode() {
    return myFetchMode;
  }

  public void setFetchMode(@NotNull FetchMode fetchMode) {
    myFetchMode = fetchMode;
  }

  public static enum FetchMode {
    FETCH_REF_SPECS,
    FETCH_ALL_REFS,
    FETCH_ALL_REFS_EXCEPT_TAGS
  }
}
