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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.BulkPatchService;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class BulkPatchBuilderImpl implements BulkPatchService, GitServerExtension {
  private final ServerPluginConfig myConfig;
  private final GitVcsSupport myVcs;

  public BulkPatchBuilderImpl(@NotNull final ServerPluginConfig config, @NotNull final GitVcsSupport vcs) {
    myConfig = config;
    myVcs = vcs;

    myVcs.addExtension(this);
  }

  public void buildPatches(@NotNull final VcsRoot root,
                           @NotNull final CheckoutRules rules,
                           @NotNull final List<BulkPatchBuilderRequest> requests,
                           @NotNull final BulkPatchBuilder patch) throws VcsException, IOException {
    final OperationContext ctx = myVcs.createContext(root, "bulk patch " + requests.size() + " commits");
    GitVcsRoot gitRoot = ctx.getGitRoot();
    myVcs.getRepositoryManager().runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        final Repository myRepo = ctx.getRepository();
        final ObjectReader contentsReader = myRepo.getObjectDatabase().newReader();
        final ObjectReader treesReader = myRepo.getObjectDatabase().newReader();

        for (BulkPatchBuilderRequest request : requests) {
          final PatchBuilder patchBuilder = patch.startPatch(request);

          final String prevBase = request.getFromVersion();
          final String toBase = request.getToVersion();

          try {
            new GitPatchBuilder(ctx, patchBuilder, prevBase, toBase, rules, myConfig.verboseTreeWalkLog()) {
              @NotNull
              @Override
              protected ObjectReader newObjectReaderForTree() {
                return treesReader;
              }

              @NotNull
              @Override
              protected ContentLoaderFactory contentLoaderFactory() {
                return new ContentLoaderFactory() {
                  @Nullable
                  public ObjectLoader open(@NotNull final Repository repo, @NotNull final ObjectId id) throws IOException {
                    assert repo == myRepo;
                    return contentsReader.open(id);
                  }
                };
              }
            }.buildPatch();

          } catch (Throwable e) {
            throw new VcsException("Failed to build patch " + prevBase + " -> " + toBase + ". " + e.getMessage(), e);
          } finally {
            patch.endPatch(request, patchBuilder);
          }
        }
      } catch (Throwable e) {
        throw new VcsException("Failed to complete bulk patch." + e.getMessage(), e);
      } finally {
        ctx.close();
      }
    });
  }
}
