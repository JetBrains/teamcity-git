/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.process;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchMemoryProvider;
import jetbrains.buildServer.util.FileUtil;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * {@link FetchMemoryProvider.XmxStorage} implementation which stores the values inside {@link Repository#getDirectory()} folder
 */
public class RepositoryFetchXmxStorage implements FetchMemoryProvider.XmxStorage {

  public static final String PREFIX = "fetch.Xmx=";
  public static final String SUFFIX = "M";

  private static Logger LOG = Logger.getInstance(RepositoryFetchXmxStorage.class.getName());

  @NotNull final File myStorage;

  public RepositoryFetchXmxStorage(@NotNull final Repository repository) {
    this(repository.getDirectory());
  }

  public RepositoryFetchXmxStorage(@NotNull final File repoDir) {
    myStorage = new File(repoDir, "teamcity.properties");
  }

  @NotNull
  public File getStorage() {
    return myStorage;
  }

  @Nullable
  @Override
  public Integer read() {
    if (!myStorage.isFile()) return null;
    try {
      final String line = FileUtil.readText(myStorage, "UTF-8"); // TODO: we may want to use java.util.Properties here
      if (line.startsWith(PREFIX) && line.endsWith(SUFFIX)) {
        return Integer.parseInt(line.substring(PREFIX.length(), line.length() - SUFFIX.length()));
      }
    } catch (Exception e) {
      LOG.warn("Failed to read fetch xmx value from " + myStorage.getAbsolutePath(), e);
    }
    return null;
  }

  @Override
  public void write(@Nullable final Integer xmx) {
    FileUtil.delete(myStorage, 3);
    if (xmx != null) {
      final String line = String.format(PREFIX + "%d" + SUFFIX, xmx);
      try {
        FileUtil.createParentDirs(myStorage);
        FileUtil.writeFile(myStorage, line, "UTF-8");
      } catch (IOException e) {
        LOG.warn("Failed to write fetch xmx value \"" + line + "\" to " + myStorage.getAbsolutePath(), e);
      }
    }
  }
}
