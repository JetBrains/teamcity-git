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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.vcs.AgentCheckoutAbility;
import jetbrains.buildServer.agent.vcs.AgentVcsSupport;
import jetbrains.buildServer.agent.vcs.UpdateByCheckoutRules2;
import jetbrains.buildServer.agent.vcs.UpdatePolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules2 {

  private final FS myFS;
  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final GitAgentSSHService mySshService;
  private final PluginConfigFactory myConfigFactory;
  private final MirrorManager myMirrorManager;
  private final GitMetaFactory myGitMetaFactory;

  //The canCheckout() method should check that roots are not checked out in the same dir (TW-49786).
  //To do that we need to create AgentPluginConfig for each VCS root which involves 'git version'
  //command execution. Since we don't have a dedicated API for checking several roots, every root
  //is checked with all other roots. In order to avoid running n^2 'git version' commands configs
  //are cached for the build. Cache is reset when we get a new build.
  private final AtomicLong myConfigsCacheBuildId = new AtomicLong(-1); //buildId for which configs are cached
  private final ConcurrentMap<VcsRoot, AgentPluginConfig> myConfigsCache = new ConcurrentHashMap<VcsRoot, AgentPluginConfig>();//cached config per root
  private final ConcurrentMap<VcsRoot, VcsException> myConfigErrorsCache = new ConcurrentHashMap<VcsRoot, VcsException>();//cached error thrown during config creation per root

  public GitAgentVcsSupport(@NotNull FS fs,
                            @NotNull SmartDirectoryCleaner directoryCleaner,
                            @NotNull GitAgentSSHService sshService,
                            @NotNull PluginConfigFactory configFactory,
                            @NotNull MirrorManager mirrorManager,
                            @NotNull GitMetaFactory gitMetaFactory) {
    myFS = fs;
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myConfigFactory = configFactory;
    myMirrorManager = mirrorManager;
    myGitMetaFactory = gitMetaFactory;
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
    AgentPluginConfig config = myConfigFactory.createConfig(build, root);
    Map<String, String> env = getGitCommandEnv(config, build);
    GitFactory gitFactory = myGitMetaFactory.createFactory(mySshService, config, getLogger(build, config), build.getBuildTempDirectory(), env, new BuildContext(build, config));
    Pair<CheckoutMode, File> targetDirAndMode = getTargetDirAndMode(config, rules, checkoutDirectory);
    CheckoutMode mode = targetDirAndMode.first;
    File targetDir = targetDirAndMode.second;
    Updater updater;
    AgentGitVcsRoot gitRoot = new AgentGitVcsRoot(myMirrorManager, targetDir, root);
    if (config.isUseAlternates(gitRoot)) {
      updater = new UpdaterWithAlternates(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    } else if (config.isUseLocalMirrors(gitRoot)) {
      updater = new UpdaterWithMirror(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    } else {
      updater = new UpdaterImpl(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode);
    }
    updater.update();
  }


  @NotNull
  private Map<String, String> getGitCommandEnv(@NotNull AgentPluginConfig config, @NotNull AgentRunningBuild build) {
    if (config.isRunGitWithBuildEnv()) {
      return build.getBuildParameters().getEnvironmentVariables();
    } else {
      return new HashMap<String, String>(0);
    }
  }

  @NotNull
  @Override
  public AgentCheckoutAbility canCheckout(@NotNull final VcsRoot vcsRoot, @NotNull CheckoutRules checkoutRules, @NotNull final AgentRunningBuild build) {
    AgentPluginConfig config;
    try {
      config = getAndCacheConfig(build, vcsRoot);
    } catch (VcsException e) {
      return AgentCheckoutAbility.noVcsClientOnAgent(e.getMessage());
    }

    Pair<CheckoutMode, String> pathAndMode = getTargetPathAndMode(checkoutRules);
    String targetDir = pathAndMode.second;
    if (targetDir == null) {
      return AgentCheckoutAbility.notSupportedCheckoutRules("Unsupported rules for agent-side checkout: " + checkoutRules.getAsString());
    }

    if (pathAndMode.first == CheckoutMode.SPARSE_CHECKOUT && !canUseSparseCheckout(config)) {
      return AgentCheckoutAbility.notSupportedCheckoutRules("Cannot perform sparse checkout using git " + config.getGitExec().getVersion());
    }

    try {
      GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, vcsRoot);
      UpdaterImpl.checkAuthMethodIsSupported(gitRoot, config);
    } catch (VcsException e) {
      return AgentCheckoutAbility.canNotCheckout(e.getMessage());
    }

    List<VcsRootEntry> gitEntries = getGitRootEntries(build);
    if (gitEntries.size() > 1) {
      for (VcsRootEntry entry : gitEntries) {
        VcsRoot otherRoot = entry.getVcsRoot();
        if (vcsRoot.equals(otherRoot))
          continue;

        AgentPluginConfig otherConfig;
        try {
          otherConfig = getAndCacheConfig(build, otherRoot);
        } catch (VcsException e) {
          continue;//appropriate reason will be returned during otherRoot check
        }
        Pair<CheckoutMode, String> otherPathAndMode = getTargetPathAndMode(entry.getCheckoutRules());
        if (otherPathAndMode.first == CheckoutMode.SPARSE_CHECKOUT && !canUseSparseCheckout(otherConfig)) {
          continue;//appropriate reason will be returned during otherRoot check
        }
        String entryPath = otherPathAndMode.second;
        if (targetDir.equals(entryPath))
          return AgentCheckoutAbility.canNotCheckout("Cannot checkout VCS root '" + vcsRoot.getName() + "' into the same directory as VCS root '" + otherRoot.getName() + "'");
      }
    }

    return AgentCheckoutAbility.canCheckout();
  }


  private boolean isRequireSparseCheckout(@NotNull CheckoutRules rules) {
    if (!rules.getExcludeRules().isEmpty())
      return true;
    List<IncludeRule> includeRules = rules.getRootIncludeRules();
    if (includeRules.isEmpty() || includeRules.size() > 1)
      return true;
    IncludeRule rule = includeRules.get(0);
    return !"".equals(rule.getFrom()); //rule of form +:.=>dir doesn't require sparse checkout ('.' is transformed into empty string)
  }


  @NotNull
  private List<VcsRootEntry> getGitRootEntries(@NotNull AgentRunningBuild build) {
    List<VcsRootEntry> result = new ArrayList<VcsRootEntry>();
    for (VcsRootEntry entry : build.getVcsRootEntries()) {
      if (Constants.VCS_NAME.equals(entry.getVcsRoot().getVcsName()))
        result.add(entry);
    }
    return result;
  }


  @NotNull
  private GitBuildProgressLogger getLogger(@NotNull AgentRunningBuild build, @NotNull AgentPluginConfig config) {
    return new GitBuildProgressLogger(build.getBuildLogger().getFlowLogger("-1"), config.getGitProgressMode());
  }


  @NotNull
  private Pair<CheckoutMode, File> getTargetDirAndMode(@NotNull AgentPluginConfig config,
                                                       @NotNull CheckoutRules rules,
                                                       @NotNull File checkoutDir) throws VcsException {
    Pair<CheckoutMode, String> pathAndMode = getTargetPathAndMode(rules);
    String path = pathAndMode.second;
    if (path == null) {
      throw new VcsException("Unsupported checkout rules for agent-side checkout: " + rules.getAsString());
    }

    boolean canUseSparseCheckout = canUseSparseCheckout(config);
    if (pathAndMode.first == CheckoutMode.SPARSE_CHECKOUT && !canUseSparseCheckout) {
      throw new VcsException("Cannot perform sparse checkout using git " + config.getGitExec().getVersion());
    }

    File targetDir = path.length() == 0 ? checkoutDir : new File(checkoutDir, path.replace('/', File.separatorChar));
    if (!targetDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      targetDir.mkdirs();
      if (!targetDir.exists())
        throw new VcsException("Cannot create destination directory '" + targetDir + "'");
    }

    //Use sparse checkout mode if we can, without that switch from rules requiring sparse checkout
    //to simple rules (e.g. to CheckoutRules.DEFAULT) doesn't work (run AgentSideSparseCheckoutTest.
    //update_files_after_switching_to_default_rules). Probably it is a rare case when we checked out
    //a repository using sparse checkout and then cannot use sparse checkout in the next build.
    CheckoutMode mode = canUseSparseCheckout ? CheckoutMode.SPARSE_CHECKOUT : pathAndMode.first;
    return Pair.create(mode, targetDir);
  }


  @NotNull
  private AgentPluginConfig getAndCacheConfig(@NotNull AgentRunningBuild build, @NotNull VcsRoot root) throws VcsException {
    //reset cache if we get a new build
    if (build.getBuildId() != myConfigsCacheBuildId.get()) {
      myConfigsCacheBuildId.set(build.getBuildId());
      myConfigsCache.clear();
      myConfigErrorsCache.clear();
    }

    AgentPluginConfig result = myConfigsCache.get(root);
    if (result == null) {
      VcsException error = myConfigErrorsCache.get(root);
      if (error != null)
        throw error;
      try {
        result = myConfigFactory.createConfig(build, root);
      } catch (VcsException e) {
        myConfigErrorsCache.put(root, e);
        throw e;
      }
      myConfigsCache.put(root, result);
    }
    return result;
  }


  @NotNull
  private Pair<CheckoutMode, String> getTargetPathAndMode(@NotNull CheckoutRules rules) {
    if (isRequireSparseCheckout(rules)) {
      return Pair.create(CheckoutMode.SPARSE_CHECKOUT, getSingleTargetDirForSparseCheckout(rules));
    } else {
      return Pair.create(CheckoutMode.MAP_REPO_TO_DIR, rules.map(""));
    }
  }

  private boolean canUseSparseCheckout(@NotNull AgentPluginConfig config) {
    return config.isUseSparseCheckout() && !config.getGitVersion().isLessThan(UpdaterImpl.GIT_WITH_SPARSE_CHECKOUT) &&
           !config.getGitVersion().equals(UpdaterImpl.BROKEN_SPARSE_CHECKOUT);
  }

  @Nullable
  private String getSingleTargetDirForSparseCheckout(@NotNull CheckoutRules rules) {
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
