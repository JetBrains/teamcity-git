/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;


import java.io.File;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

public class ShallowUpdater extends UpdaterImpl {

  public static final String REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED = "server does not allow request for unadvertised object";

  public ShallowUpdater(@NotNull final FS fs,
                        @NotNull final AgentPluginConfig pluginConfig,
                        @NotNull final MirrorManager mirrorManager,
                        @NotNull final SmartDirectoryCleaner directoryCleaner,
                        @NotNull final GitFactory gitFactory,
                        @NotNull final AgentRunningBuild build,
                        @NotNull final VcsRoot root,
                        @NotNull final String version,
                        @NotNull final File targetDir,
                        @NotNull final CheckoutRules rules,
                        @NotNull final CheckoutMode checkoutMode,
                        final SubmoduleManager submoduleManager) throws VcsException {
    super(fs, pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, checkoutMode, submoduleManager);
  }

  @Override
  protected void ensureCommitLoaded(final boolean fetchRequired) throws VcsException {
    final FetchHeadsMode fetchHeadsMode = myPluginConfig.getFetchHeadsMode();
    if (fetchHeadsMode == FetchHeadsMode.AFTER_BUILD_BRANCH) {
      if (hasRevision(myTargetDirectory, myRevision)) {
        myLogger.debug("Revision " + myRevision + " is present in the local repository, skip fetch");
        return;
      }

      final boolean branchPointsTheRevision = isRemoteBranchPointsTheRevision();
      if (GitUtilsAgent.isTag(myFullBranchName) && branchPointsTheRevision) {
        fetch(myTargetDirectory, getRefspecForFetch(), true);
      } else {
        try {
          fetch(myTargetDirectory, getRefSpecForRevision(), true);
        } catch (VcsException e) {
          if (isRequestNotAllowed(e)) {
            myLogger.warning(StringUtil.capitalize(REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED) + ": to speed-up the checkout configure your remote repository to allow directly fetching commits (set uploadpack.allowReachableSHA1InWant or uploadpack.allowAnySHA1InWant config variables to true in the remote git config)");

            if (branchPointsTheRevision) {
              fetch(myTargetDirectory, getRefspecForFetch(), true);
            }
          } else throw e;
        }
      }

      if (hasRevision(myTargetDirectory, myRevision)) {
        return;
      }

      myLogger.debug("Failed to get the revision " + myRevision + " using shallow fetch, will try regular fetch");
    } else {
      myLogger.warning("Shallow fetch won't be performed because " + PluginConfigImpl.FETCH_ALL_HEADS + " parameter is set to " + myPluginConfig.getFetchAllHeadsModeStr() + ", which is incompatible with shallow clone.");
    }
    super.ensureCommitLoaded(fetchRequired);
  }

  @NotNull
  private String getRefSpecForRevision() {
    if (GitUtilsAgent.isTag(myFullBranchName)) {
      return myRevision;
    }
    return "+" + myRevision + ":" + GitUtils.createRemoteRef(myFullBranchName);
  }

  private boolean isRemoteBranchPointsTheRevision() throws VcsException {
    return getRemoteRefs(myTargetDirectory).list().stream().anyMatch(r -> myFullBranchName.equals(r.getName()) && myRevision.equals(r.getObjectId().getName()));
  }

  private boolean isRequestNotAllowed(@NotNull VcsException e) {
    final String msg = e.getMessage();
    return msg != null && msg.toLowerCase().contains(REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED);
  }
}
