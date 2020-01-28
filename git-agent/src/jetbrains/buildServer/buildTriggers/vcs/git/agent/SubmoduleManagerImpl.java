/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class SubmoduleManagerImpl implements SubmoduleManager {

  private final static Logger LOG = Logger.getInstance(SubmoduleManagerImpl.class.getName());

  @NotNull private final MirrorManager myMirrorManager;

  public SubmoduleManagerImpl(@NotNull final MirrorManager mirrorManager) {
    myMirrorManager = mirrorManager;
  }

  @Override
  public synchronized void persistSubmodules(@NotNull final String repositoryUrl, @NotNull final Collection<String> submodules) {
    final File submoduleFile = getSubmoduleFile(repositoryUrl);
    try {
      FileUtil.writeFile(submoduleFile, StringUtil.join("\n", submodules), "UTF-8");
    } catch (IOException e) {
      LOG.warn("Failed to persist " + repositoryUrl + " submodules to " + submoduleFile + ": " + e.getMessage());
    }
  }

  @NotNull
  private File getSubmoduleFile(@NotNull final String repositoryUrl) {
    return new File(myMirrorManager.getMirrorDir(repositoryUrl), "submodules");
  }

  @NotNull
  @Override
  public synchronized Collection<String> getSubmodules(@NotNull final String repositoryUrl) {
    final File submoduleFile = getSubmoduleFile(repositoryUrl);
    if (submoduleFile.isFile()) {
      try {
        return new HashSet<String>(FileUtil.readFile(submoduleFile, "UTF-8"));
      } catch (IOException e) {
        LOG.warn("Failed to read " + repositoryUrl + " submodules from " + submoduleFile + ": " + e.getMessage());
      }
    }
    return Collections.emptySet();
  }
}