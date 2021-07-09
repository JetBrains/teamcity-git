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
import java.io.IOException;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.ConfigInvalidException;
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
    throwNoCommitFoundIfNecessary(getCommitLoader(myTargetDirectory).loadCommitPreferShallow(myRevision, myFullBranchName));
  }

  @Override
  protected void updateSubmodules(@NotNull final File repositoryDir) throws VcsException, ConfigInvalidException, IOException {
    AgentGitFacade git = myGitFactory.create(repositoryDir);
    SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate()
                                                .setAuthSettings(myRoot.getAuthSettings())
                                                .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
                                                .setTimeout(myPluginConfig.getSubmoduleUpdateTimeoutSeconds())
                                                .setForce(isForceUpdateSupported())
                                                .setDepth(1);
    configureLFS(submoduleUpdate);
    submoduleUpdate.call();
  }
}
