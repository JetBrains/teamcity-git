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

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created 20.02.14 15:28
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class CachedDotGitModulesResolver implements DotGitModulesResolver {
  private final DotGitModulesResolver myBase;
  private final ObjectIdOwnerMap<Entry> myCache = new ObjectIdOwnerMap<Entry>();

  private static class Entry extends ObjectIdOwnerMap.Entry {
    private final SubmodulesConfig myConfig;

    private Entry(@NotNull final AnyObjectId id,
                  @Nullable final SubmodulesConfig config) {
      super(id);
      myConfig = config;
    }

    @Nullable
    public SubmodulesConfig getConfig() {
      return myConfig;
    }
  }

  public CachedDotGitModulesResolver(@NotNull final DotGitModulesResolver base) {
    myBase = base;
  }

  @Nullable
  public SubmodulesConfig forBlob(@NotNull final AnyObjectId blob) throws IOException {
    final Entry cached = myCache.get(blob);
    if (cached != null) return cached.getConfig();

    final SubmodulesConfig value = myBase.forBlob(blob);
    myCache.add(new Entry(blob, value));
    return value;
  }
}
