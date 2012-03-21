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

import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
* @author dmitry.neverov
*/
public class GitFile implements Element {

  private final GitVcsSupport myGit;
  private final VcsRoot myRoot;
  private final String myRevision;
  private final File myFile;

  public GitFile(@NotNull GitVcsSupport git, @NotNull VcsRoot root, @NotNull String revision, @NotNull File file) {
    myGit = git;
    myRoot = root;
    myRevision = revision;
    myFile = file;
  }

  @NotNull
  public String getName() {
    return myFile.getName();
  }

  @NotNull
  public String getFullName() {
    return myFile.getPath();
  }

  public boolean isLeaf() {
    return true;
  }

  @Nullable
  public Iterable<Element> getChildren() {
    return null;
  }

  public boolean isContentAvailable() {
    return true;
  }

  @NotNull
  public InputStream getInputStream() throws IllegalStateException, IOException {
    try {
      return new ByteArrayInputStream(myGit.getContent(getFullName(), myRoot, myRevision));
    } catch (VcsException e) {
      throw new IOException(e);
    }
  }

  public long getSize() throws IllegalStateException {
    try {
      return myGit.getContent(getFullName(), myRoot, myRevision).length;
    } catch (VcsException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public String getRevision() {
    return myRevision;
  }

  @NotNull
  public VcsRoot getRoot() {
    return myRoot;
  }

  @NotNull
  public GitVcsSupport getGit() {
    return myGit;
  }

  @Override
  public String toString() {
    return getFullName();
  }
}
