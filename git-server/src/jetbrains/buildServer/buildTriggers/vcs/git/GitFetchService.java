/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.FetchService;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

/**
 * Created 28.04.2014 20:26
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class GitFetchService implements FetchService, GitServerExtension {
  @NotNull private final GitVcsSupport myVcs;

  public GitFetchService(@NotNull final GitVcsSupport support) {
    myVcs = support;
    support.addExtension(this);
  }

  public void fetchRepository(@NotNull final VcsRoot root,
                              @NotNull final CheckoutRules rules,
                              @NotNull final FetchRepositoryCallback callback) throws VcsException {

    final OperationContext ctx = myVcs.createContext(root, "Fetch");
    try {

      //TODO: make fetch provide progress even if the fetch was called in a external process

      final Repository db = ctx.getRepository();
      myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, db, false, myVcs.getCurrentState(ctx.makeRootWithTags()));

    } catch (Exception e) {
      throw new VcsException("Failed to fetch repository. " + e.getMessage(), e);
    } finally {
      ctx.close();
    }
  }
}
