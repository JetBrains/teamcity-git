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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;

/**
 * Exception is thrown when we cannot fetch some submodule
 *
 * @author dmitry.neverov
 */
public class SubmoduleFetchException extends CorruptObjectException {

  public SubmoduleFetchException(@NotNull String mainRepositoryUrl,
                                 @NotNull String submodulePath,
                                 @NotNull String submoduleUrl,
                                 @NotNull ObjectId mainRepositoryCommit,
                                 @NotNull Throwable cause) {
    super("Cannot fetch the '" + submoduleUrl
          + "' repository used as a submodule at the '" + submodulePath
          + "' path in the '" + mainRepositoryUrl
          + "' repository in the " + mainRepositoryCommit.name() + " commit"
          + ", cause: " + cause.getClass().getName() + ": " + cause.getMessage());
    initCause(cause);
  }

  public SubmoduleFetchException(@NotNull String mainRepositoryUrl,
                                 @NotNull String submodulePath,
                                 @NotNull String submodulePathFromRoot) {
    super(getMessage(mainRepositoryUrl, submodulePath, submodulePathFromRoot));
  }


  private static String getMessage(String repositoryUrl, String submodulePath, String submodulePathFromRoot) {
    if (submodulePath.equals(submodulePathFromRoot)) {
      return String.format("Cannot fetch submodule. Repository URL: '%s', submodule path: '%s'.",
                           repositoryUrl, submodulePath);
    } else {
      return String.format("Cannot fetch submodule. Repository URL: '%s', submodule path: '%s', submodule path from the root: '%s'.",
                           repositoryUrl, submodulePath, submodulePathFromRoot);
    }
  }

}
