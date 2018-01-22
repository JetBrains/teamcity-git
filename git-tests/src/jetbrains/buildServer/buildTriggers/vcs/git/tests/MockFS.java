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

import jetbrains.buildServer.buildTriggers.vcs.git.agent.FS;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MockFS implements FS {

  private final Set<File> myDeleteToFail = new HashSet<File>();

  @Override
  public boolean delete(@NotNull final File f) {
    if (myDeleteToFail.contains(f))
      return false;
    return FileUtil.delete(f);
  }

  @Override
  public boolean mkdirs(@NotNull File dir) {
    return dir.mkdirs();
  }

  public void makeDeleteFail(@NotNull File f) {
    myDeleteToFail.add(f);
  }
}
