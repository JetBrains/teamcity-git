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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.errors;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class Errors {

  private static final Pattern OUTDATED_INDEX_PATTERN = Pattern.compile(".*Entry '.+' not uptodate\\. Cannot merge\\..*", Pattern.DOTALL);

  public static boolean isCorruptedIndexError(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null)
      return false;
    return msg.contains("fatal: index file smaller than expected");
  }


  public static boolean isOutdatedIndexError(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null)
      return false;
    return OUTDATED_INDEX_PATTERN.matcher(msg).matches();
  }

}
