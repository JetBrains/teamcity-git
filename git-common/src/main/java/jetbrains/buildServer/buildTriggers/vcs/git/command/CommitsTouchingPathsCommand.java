/*
 * Copyright 2000-2025 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CommitsTouchingPathsCommand  {
  @NotNull
  CommitsTouchingPathsCommand setStartCommit(@NotNull String commit);

  @NotNull
  CommitsTouchingPathsCommand setExcludedCommits(@NotNull Collection<String> excludedCommits);

  /**
   * Sets the maximum number of commits to return
   * @param maxCommits max commits to return
   */
  @NotNull
  CommitsTouchingPathsCommand setMaxCommits(int maxCommits);

  /**
   * Accepts a collection of file paths and returns a list of commits which touched any of these files.
   * @param paths file paths
   * @return list of shas of commits touching any of the specified file paths
   * @throws VcsException
   */
  @NotNull
  List<String> call(@NotNull Collection<String> paths) throws VcsException;
}
