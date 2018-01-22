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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.vcs.VcsFileModification;
import jetbrains.buildServer.vcs.VcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class MockVcsModification implements VcsModification {
  private String myVersion;

  public MockVcsModification(@NotNull String version) {
    myVersion = version;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  public long getId() {
    throw new UnsupportedOperationException();
  }

  public boolean isPersonal() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public List<VcsFileModification> getChanges() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VcsFileModification findChangeByPath(final String fileName) {
    throw new UnsupportedOperationException();
  }

  public int getChangeCount() {
    throw new UnsupportedOperationException();
  }

  public String getDisplayVersion() {
    throw new UnsupportedOperationException();
  }

  public String getVersionControlName() {
    throw new UnsupportedOperationException();
  }

  public int compareTo(final VcsModification o) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getUserName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public String getDescription() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Date getVcsDate() {
    throw new UnsupportedOperationException();
  }
}
