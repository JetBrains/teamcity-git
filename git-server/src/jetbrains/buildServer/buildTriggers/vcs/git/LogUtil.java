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

import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.Nullable;

/**
 * Created 01.10.12 16:28
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class LogUtil {
  private static final String NULL_OBJECT = "<null>";

  /**
   * Describe VcsRoots in logs
   * @param root VCS root to describe
   * @return VCS root representation which allows to identify it among other VCS roots
   * */
  public static String describe(@Nullable final VcsRoot root) {
    return root == null ? NULL_OBJECT : root.toString();
  }

  public static String describe(@Nullable final GitVcsRoot root) {
    return root == null ? NULL_OBJECT : root.toString();
  }
}
