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

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Map file for URL => -Xmx for separate fetch process
 *
 * @author Mikhail Khorkov
 * @since 2019.2
 */
public interface MemoryStorage {
  /**
   * Gets last saved memory value for specified url.
   *
   * @param url url for find in storage
   * @return last saved memory value for specified url or <code>null</code>
   */
  @Nullable
  Long getCachedMemoryValue(@NotNull String url);

  /**
   * Sets memory value for specified url.
   *
   * @param url   url to save
   * @param value amount of memory save
   */
  void setCachedMemoryValue(@NotNull String url, @NotNull Long value);

  /**
   * Remove memory value for specified url.
   * @param url url to remove value
   */
  void deleteCachedMemoryValue(@NotNull String url);
}
