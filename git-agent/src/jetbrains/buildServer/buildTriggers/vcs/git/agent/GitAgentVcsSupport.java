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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.vcs.AgentVcsSupport;
import jetbrains.buildServer.agent.vcs.UpdateByCheckoutRules2;
import jetbrains.buildServer.agent.vcs.UpdatePolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules2 {

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final GitAgentSSHService mySshService;
  private final PluginConfigFactory myConfigFactory;
  private final MirrorManager myMirrorManager;

  public GitAgentVcsSupport(@NotNull SmartDirectoryCleaner directoryCleaner,
                            @NotNull GitAgentSSHService sshService,
                            @NotNull PluginConfigFactory configFactory,
                            @NotNull MirrorManager mirrorManager) {
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myConfigFactory = configFactory;
    myMirrorManager = mirrorManager;
  }


  @NotNull
  @Override
  public UpdatePolicy getUpdatePolicy() {
    return this;
  }


  @NotNull
  @Override
  public String getName() {
    return Constants.VCS_NAME;
  }


  public void updateSources(@NotNull VcsRoot root,
                            @NotNull CheckoutRules rules,
                            @NotNull String toVersion,
                            @NotNull File checkoutDirectory,
                            @NotNull AgentRunningBuild build,
                            boolean cleanCheckoutRequested) throws VcsException {
    validateCheckoutRules(root, rules);
    File targetDir = getTargetDir(root, rules, checkoutDirectory);
    AgentPluginConfig config = myConfigFactory.createConfig(build, root);
    GitFactory gitFactory = new GitFactoryImpl(mySshService, config, build.getBuildTempDirectory());
    Updater updater;
    if (config.isUseAlternates()) {
      updater = new UpdaterWithAlternates(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir);
    } else if (config.isUseLocalMirrors()) {
      updater = new UpdaterWithMirror(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir);
    } else {
      updater = new UpdaterImpl(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir);
    }
    updater.update();
  }


  /**
   * Check if specified checkout rules are supported
   * @param root root for which rules are checked
   * @param rules rules to check
   * @throws VcsException rules are not supported
   */
  private void validateCheckoutRules(@NotNull final VcsRoot root, @NotNull final CheckoutRules rules) throws VcsException {
    if (rules.getExcludeRules().size() != 0) {
      throw new VcsException("Exclude rules are not supported for agent checkout for the git (" + rules.getExcludeRules().size() +
                             " rule(s) detected) for VCS Root '" + root.getName() + "'");
    }
    if (rules.getIncludeRules().size() > 1) {
      throw new VcsException("At most one include rule is supported for agent checkout for the git (" + rules.getIncludeRules().size() +
                             " rule(s) detected) for VCS Root '" + root.getName() + "'");
    }
    if (rules.getIncludeRules().size() == 1) {
      IncludeRule ir = rules.getIncludeRules().get(0);
      if (!".".equals(ir.getFrom()) && ir.getFrom().length() != 0) {
        throw new VcsException("Agent checkout for the git supports only include rule of form '. => subdir', rule '" + ir.toDescriptiveString() +
                               "' for VCS Root '" + root.getName() + "' is not supported");
      }
    }
  }


  /**
   * Get the destination directory creating it if it is missing
   * @param root VCS root
   * @param rules checkout rules
   * @param checkoutDirectory checkout directory for the build
   * @return the directory where vcs root should be checked out according to checkout rules
   * @throws VcsException if the directory could not be located or created
   */
  private File getTargetDir(@NotNull final VcsRoot root, @NotNull final CheckoutRules rules, @NotNull final File checkoutDirectory) throws VcsException {
    String path = rules.map("");
    if (path == null)
      throw new VcsException("The root path could not be mapped for VCS root '" + root.getName() + "'");

    File directory = path.length() == 0 ? checkoutDirectory : new File(checkoutDirectory, path.replace('/', File.separatorChar));
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
      if (!directory.exists())
        throw new VcsException("The destination directory '" + directory + "' could not be created.");
    }
    return directory;
  }
}
