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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
* Created 20.02.14 12:35
*
* @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
*/
public class DotGitModulesResolverImpl implements DotGitModulesResolver {
  private static final Logger LOG = Logger.getInstance(DotGitModulesResolverImpl.class.getName());

  private final Repository myDb;

  public DotGitModulesResolverImpl(@NotNull final Repository db) {
    myDb = db;
  }

  @Nullable
  public SubmodulesConfig forBlob(@NotNull final AnyObjectId blob) throws IOException {
    try {
      return new SubmodulesConfig(myDb.getConfig(), new BlobBasedConfig(null, myDb, blob));
    } catch (ConfigInvalidException e) {
      LOG.info("Invalid submodule config: " + e.getMessage(), e);
      return null;
    }
  }
}
