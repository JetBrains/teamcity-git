/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.RepositoryMatcher;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * @author dmitry.neverov
 */
public class GitRepositoryMatcher implements RepositoryMatcher {

  @NotNull
  public String getRepository(@NotNull VcsRoot root) {
    return root.getProperty(Constants.FETCH_URL, "");
  }

  public boolean matches(@NotNull VcsRoot root, @NotNull String repository) {
    String fetchUrl = root.getProperty(Constants.FETCH_URL);
    if (isEmpty(fetchUrl))
      return false;
    return repository.equals(fetchUrl);
  }
}
