/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.GitServerExtension;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class BulkPatchBuilderImpl implements GitServerExtension {
  private final ServerPluginConfig myConfig;
  private final GitVcsSupport myVcs;

  public BulkPatchBuilderImpl(@NotNull final ServerPluginConfig config, @NotNull final GitVcsSupport vcs) {
    myConfig = config;
    myVcs = vcs;

    myVcs.addExtension(this);
  }

  public interface BulkPatchBuilder extends PatchBuilder {
    void startPatch(@NotNull final String version,
                    @Nullable final String base) throws IOException;

    void endPatch() throws IOException;
  }


  public void buildIncrementalPatch(@NotNull final VcsRoot root,
                                    @NotNull final CheckoutRules rules,
                                    @NotNull final List<String> revisions,
                                    @Nullable final String knownBaseRevision,
                                    @NotNull final BulkPatchBuilder patchBuilder) throws VcsException {
    @Nullable String prevBase = knownBaseRevision;
    final OperationContext ctx = myVcs.createContext(root, "bulk patch " + revisions.size() + " commits");
    try {
      for (String rev : revisions) {

        patchBuilder.startPatch(rev, prevBase);

        new GitPatchBuilder(ctx, patchBuilder, prevBase, rev, rules, myConfig.verboseTreeWalkLog())
          .buildPatch();

        patchBuilder.endPatch();
        prevBase = rev;
      }
    } catch (Throwable e) {
      throw new VcsException("Failed to complete bulk patch at revision " + prevBase + ". " + e.getMessage(), e);
    } finally {
      ctx.close();
    }
  }
}
