/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/**
 * Exception is thrown when we cannot fetch some submodule
 *
 * @author dmitry.neverov
 */
public class SubmoduleFetchException extends CorruptObjectException {

  public SubmoduleFetchException(String repositoryUrl, String submodulePath, String submodulePathFromTheRoot) {
    super(getMessage(repositoryUrl, submodulePath, submodulePathFromTheRoot));
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
