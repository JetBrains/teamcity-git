/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface SubmoduleManager {

  /**
   * Persists the provided submodules into "teamcity.submodules" file inside the provided repository mirror
   *
   * @param repositoryUrl parent repository
   * @param submodules set of submodule URLs
   */
  void persistSubmodules(@NotNull String repositoryUrl, @NotNull Collection<String> submodules);

  /**
   * Returns submodules specified inside "teamcity.submodules" file inside the provided repository mirror
   * @param repositoryUrl parent repository
   * @return set of submodule URLs
   */
  @NotNull
  Collection<String> getSubmodules(@NotNull String repositoryUrl);
}
