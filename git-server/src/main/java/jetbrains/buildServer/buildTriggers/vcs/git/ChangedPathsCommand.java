/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ChangedPathsCommand extends GitCommand {
  @NotNull
  Collection<String> changedPaths(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull String startRevision, @NotNull Collection<String> excludedRevisions)
    throws VcsException;

  @NotNull
  Collection<String> commitsByPaths(@NotNull final Repository db,
                                    @NotNull final GitVcsRoot gitRoot,
                                    @NotNull final String startRevision,
                                    @NotNull final Collection<String> excludedRevisions,
                                    int maxCommits,
                                    @NotNull Collection<String> paths)
    throws VcsException;
}
