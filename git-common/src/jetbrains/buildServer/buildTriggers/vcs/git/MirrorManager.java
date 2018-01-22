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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Manages local mirror dirs of remote repositories
 * @author dmitry.neverov
 */
public interface MirrorManager {

  /**
   * @return parent dir of local repositories
   */
  @NotNull
  File getBaseMirrorsDir();

  /**
   * Get default directory for remote repository with specified url
   * @param repositoryUrl remote repository url
   * @return see above
   */
  @NotNull
  File getMirrorDir(@NotNull String repositoryUrl);

  /**
   * Mark dir as invalid, urls mapped to this dir will get another mapping
   * on subsequent call to getMirrorDir()
   * @param dir dir of interest
   */
  void invalidate(@NotNull File dir);


  @NotNull
  Map<String, File> getMappings();

  long getLastUsedTime(@NotNull final File dir);

  /**
   * Returns url for the given clone directory name inside the baseMirrorsDir
   * or null if mapping from the url is not found
   * @param cloneDirName clone directory name of interest
   * @return see above
   */
  @Nullable
  String getUrl(@NotNull String cloneDirName);
}
