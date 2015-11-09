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

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules2 {

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final GitAgentSSHService mySshService;
  private final GitDetector myGitDetector;
  private final MirrorManager myMirrorManager;
  private final GitMetaFactory myGitMetaFactory;
  private final BuildAgentConfiguration myAgentConfig;

  public GitAgentVcsSupport(@NotNull SmartDirectoryCleaner directoryCleaner,
                            @NotNull GitAgentSSHService sshService,
                            @NotNull GitDetector gitDetector,
                            @NotNull MirrorManager mirrorManager,
                            @NotNull GitMetaFactory gitMetaFactory,
                            @NotNull BuildAgentConfiguration agentConfig) {
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myGitDetector = gitDetector;
    myMirrorManager = mirrorManager;
    myGitMetaFactory = gitMetaFactory;
    myAgentConfig = agentConfig;
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
    GitExec gitExec = myGitDetector.getGitPathAndVersion(root, myAgentConfig, build);
    AgentPluginConfig config = new PluginConfigImpl(myAgentConfig, build, gitExec);
    GitFactory gitFactory = myGitMetaFactory.createFactory(mySshService, config, getLogger(build), build.getBuildTempDirectory());
    Pair<CheckoutMode, File> targetDirAndMode = getTargetDirAndMode(config, root, rules, checkoutDirectory);
    CheckoutMode mode = targetDirAndMode.first;
    File targetDir = targetDirAndMode.second;
    Updater updater;
    AgentGitVcsRoot gitRoot = new AgentGitVcsRoot(myMirrorManager, targetDir, root);
    if (config.isUseAlternates(gitRoot)) {
      updater = new UpdaterWithAlternates(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    } else if (config.isUseLocalMirrors(gitRoot)) {
      updater = new UpdaterWithMirror(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    } else {
      updater = new UpdaterImpl(config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    }
    updater.update();
  }

  @NotNull
  private GitBuildProgressLogger getLogger(@NotNull AgentRunningBuild build) {
    return new GitBuildProgressLogger(build.getBuildLogger().getFlowLogger("-1"));
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


  @NotNull
  private Pair<CheckoutMode, File> getTargetDirAndMode(@NotNull AgentPluginConfig config,
                                                       @NotNull VcsRoot root,
                                                       @NotNull CheckoutRules rules,
                                                       @NotNull File checkoutDir) throws VcsException {
    GitVersion version = config.getGitVersion();
    if (config.isUseSparseCheckout() && !version.isLessThan(UpdaterImpl.GIT_WITH_SPARSE_CHECKOUT)) {
      String targetDir = getSingleTargetDir(rules);
      if (targetDir != null) {
        return Pair.create(CheckoutMode.SPARSE_CHECKOUT, new File(checkoutDir, targetDir));
      }
    }

    validateCheckoutRules(root, rules);
    File targetDir = getTargetDir(root, rules, checkoutDir);
    return Pair.create(CheckoutMode.MAP_REPO_TO_DIR, targetDir);
  }


  @Nullable
  private String getSingleTargetDir(@NotNull CheckoutRules rules) {
    Set<String> targetDirs = new HashSet<String>();
    for (IncludeRule rule : rules.getRootIncludeRules()) {
      String from = rule.getFrom();
      String to = rule.getTo();
      if (from.equals("")) {
        targetDirs.add(to);
        continue;
      }
      if (from.equals(to)) {
        targetDirs.add("");
        continue;
      }
      int prefixEnd = to.lastIndexOf(from);
      if (prefixEnd == -1) // rule of form +:a=>b, but we don't support such mapping
        return null;
      String prefix = to.substring(0, prefixEnd);
      if (!prefix.endsWith("/")) //rule of form +:a=>ab, but we don't support such mapping
        return null;
      prefix = prefix.substring(0, prefix.length() - 1);
      targetDirs.add(prefix);
    }
    if (targetDirs.isEmpty())
      return "";
    if (targetDirs.size() > 1) //no single target dir
      return null;
    return targetDirs.iterator().next();
  }
}
