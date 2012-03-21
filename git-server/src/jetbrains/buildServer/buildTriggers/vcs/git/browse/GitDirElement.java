/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.browse;

import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author dmitry.neverov
 */
public abstract class GitDirElement implements Element {

  protected final File myDir;

  public GitDirElement(@NotNull File dir) {
    myDir = dir;
  }

  @NotNull
  public final String getName() {
    return myDir.getName();
  }

  @NotNull
  public final String getFullName() {
    return myDir.getPath();
  }

  public final boolean isLeaf() {
    return false;
  }

  public final boolean isContentAvailable() {
    return false;
  }

  @NotNull
  public final InputStream getInputStream() throws IllegalStateException, IOException {
    throw new IllegalStateException(myDir.getPath() + " is a directory, content is not available");
  }

  public final long getSize() throws IllegalStateException {
    throw new IllegalStateException(myDir.getPath() + " is a directory, size is not available");
  }

  @Override
  public String toString() {
    return myDir.getPath();
  }
}
