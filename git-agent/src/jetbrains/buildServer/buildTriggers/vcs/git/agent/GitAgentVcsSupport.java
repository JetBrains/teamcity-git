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
import com.jcraft.jsch.JSch;
import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.agent.vcs.AgentCheckoutAbility;
import jetbrains.buildServer.agent.vcs.AgentVcsSupport;
import jetbrains.buildServer.agent.vcs.UpdateByCheckoutRules2;
import jetbrains.buildServer.agent.vcs.UpdatePolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CleanCommandUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules2 {

  private final FS myFS;
  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final GitAgentSSHService mySshService;
  private final PluginConfigFactory myConfigFactory;
  private final MirrorManager myMirrorManager;
  private final SubmoduleManager mySubmoduleManager;
  private final GitMetaFactory myGitMetaFactory;
  private final AgentTokenStorage myTokenStorage;

  //The canCheckout() method should check that roots are not checked out in the same dir (TW-49786).
  //To do that we need to create AgentPluginConfig for each VCS root which involves 'git version'
  //command execution. Since we don't have a dedicated API for checking several roots, every root
  //is checked with all other roots. In order to avoid running n^2 'git version' commands configs
  //are cached for the build. Cache is reset when we get a new build or on preparationFinished event.
  private final AtomicLong myConfigsCacheBuildId = new AtomicLong(-1); //buildId for which configs are cached
  private final ConcurrentMap<VcsRoot, AgentPluginConfig> myConfigsCache = new ConcurrentHashMap<VcsRoot, AgentPluginConfig>();//cached config per root
  private final ConcurrentMap<VcsRoot, VcsException> myConfigErrorsCache = new ConcurrentHashMap<VcsRoot, VcsException>();//cached error thrown during config creation per root

  final static String switchCheckoutModeMessage = "Fix the checkout rules to use them with agent-side checkout or enable \"Auto\" VCS checkout mode.";

  final static String agentCheckoutRulesErrorMessage = "The checkout rule '%s' is unsupported for agent-side checkout mode. " +
                                                       "The rules 'a=>[prefix/]a/postfix' are unsupported. Only the rules 'a=>[prefix/]a' are supported for agent-side checkout, the [prefix/] must be the same for all rules. " + switchCheckoutModeMessage;


  public GitAgentVcsSupport(@NotNull FS fs,
                            @NotNull SmartDirectoryCleaner directoryCleaner,
                            @NotNull GitAgentSSHService sshService,
                            @NotNull PluginConfigFactory configFactory,
                            @NotNull MirrorManager mirrorManager,
                            final SubmoduleManager submoduleManager,
                            @NotNull GitMetaFactory gitMetaFactory,
                            @NotNull EventDispatcher<AgentLifeCycleListener> agentEventDispatcher,
                            @NotNull AgentTokenStorage tokenStorage) {
    myFS = fs;
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myConfigFactory = configFactory;
    myMirrorManager = mirrorManager;
    mySubmoduleManager = submoduleManager;
    myGitMetaFactory = gitMetaFactory;
    myTokenStorage = tokenStorage;

    agentEventDispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void preparationFinished(@NotNull final AgentRunningBuild runningBuild) {
        clearPluginConfigCache();
      }
    });

    JSchConfigInitializer.initJSchConfig(JSch.class);
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
    GitFactory gitFactory = myGitMetaFactory.createFactory(mySshService, new BuildContext(build, config));
    Pair<CheckoutMode, File> targetDirAndMode = getTargetDirAndMode(config, rules, checkoutDirectory);
    CheckoutMode mode = targetDirAndMode.first;
    File targetDir = targetDirAndMode.second;
    Updater updater;
    AgentGitVcsRoot gitRoot = new AgentGitVcsRoot(myMirrorManager, targetDir, root, myTokenStorage);
    if (config.isUseShallowClone(gitRoot)) {
      updater = new ShallowUpdater(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode, mySubmoduleManager, myTokenStorage);
    } else if (config.isUseAlternates(gitRoot)) {
      updater = new UpdaterWithAlternates(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode, mySubmoduleManager, myTokenStorage);
    } else if (config.isUseLocalMirrors(gitRoot)) {
      updater = new UpdaterWithMirror(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode, mySubmoduleManager, myTokenStorage);
    } else {
      updater = new UpdaterImpl(myFS, config, myMirrorManager, myDirectoryCleaner, gitFactory, build, root, toVersion, targetDir, rules, mode, mySubmoduleManager, myTokenStorage);
    }
    updater.update();
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
      GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, vcsRoot, new URIishHelperImpl());
      UpdaterImpl.checkAuthMethodIsSupported(gitRoot, config);
    } catch (VcsException e) {
      return AgentCheckoutAbility.canNotCheckout(e.getMessage());
    }

    final List<VcsRootEntry> rootEntries = build.getVcsRootEntries();
    if (rootEntries.size() > 1) {
      for (VcsRootEntry entry : rootEntries) {
        VcsRoot otherRoot = entry.getVcsRoot();
        if (vcsRoot.equals(otherRoot))
          continue;

        if (isGitVcsRoot(entry)) {
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

        } else if (CleanCommandUtil.isCleanEnabled(vcsRoot) && CleanCommandUtil.isClashingTargetPath(targetDir, entry, config.getGitVersion())) {
          // in this case git clean command may remove files which belong to the other root
          return AgentCheckoutAbility.canNotCheckout("Cannot checkout VCS root '" + vcsRoot.getName() + "' into the same directory as VCS root '" + otherRoot.getName() + "'");
        }
      }
    }

    return AgentCheckoutAbility.canCheckout();
  }

  private boolean isGitVcsRoot(@NotNull VcsRootEntry entry) {
    return Constants.VCS_NAME.equals(entry.getVcsRoot().getVcsName());
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
  private Pair<CheckoutMode, File> getTargetDirAndMode(@NotNull AgentPluginConfig config,
                                                       @NotNull CheckoutRules rules,
                                                       @NotNull File checkoutDir) throws VcsException {
    Pair<CheckoutMode, String> pathAndMode;
    try {
      pathAndMode = getTargetPathAndModeForAgentSideCheckout(rules, config.shouldIgnoreCheckoutRulesPostfixCheck());
    } catch (VcsException e) {
      throw new VcsException(e.getMessage());
    }
    String path = pathAndMode.second;

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
      clearPluginConfigCache();
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

  private void clearPluginConfigCache() {
    myConfigsCache.clear();
    myConfigErrorsCache.clear();
  }


  @NotNull
  private Pair<CheckoutMode, String> getTargetPathAndMode(@NotNull CheckoutRules rules) {
    if (isRequireSparseCheckout(rules)) {
      String targetPath = null;
      try {
        targetPath = processCheckoutRulesForAgentSideCheckout(rules, false);
      } catch (VcsException ignored) { }
      return Pair.create(CheckoutMode.SPARSE_CHECKOUT, targetPath);
    } else {
      return Pair.create(CheckoutMode.MAP_REPO_TO_DIR, rules.map(""));
    }
  }

  @NotNull
  private Pair<CheckoutMode, String> getTargetPathAndModeForAgentSideCheckout(@NotNull CheckoutRules rules, boolean ignorePostfixInRules) throws VcsException {
    if (isRequireSparseCheckout(rules)) {
      return Pair.create(CheckoutMode.SPARSE_CHECKOUT, processCheckoutRulesForAgentSideCheckout(rules, ignorePostfixInRules));
    } else {
      return Pair.create(CheckoutMode.MAP_REPO_TO_DIR, rules.map(""));
    }
  }

  private boolean canUseSparseCheckout(@NotNull AgentPluginConfig config) {
    return config.isUseSparseCheckout() && !config.getGitVersion().isLessThan(UpdaterImpl.GIT_WITH_SPARSE_CHECKOUT) &&
           !config.getGitVersion().equals(UpdaterImpl.BROKEN_SPARSE_CHECKOUT);
  }

  private static String processCheckoutRulesForAgentSideCheckout(@NotNull CheckoutRules rules, boolean ignorePostfixInRules) throws VcsException {
    String targetDir = null;
    IncludeRule previousRule = null;
    for (IncludeRule rule : rules.getRootIncludeRules()) {
      String from = rule.getFrom();
      String to = rule.getTo();
      if (from.equals("")) {
        targetDir = assignTargetDir(targetDir, to, rule, previousRule);
      }
      else if (from.equals(to)) {
        targetDir = assignTargetDir(targetDir, "", rule, previousRule);
      }
      else {
        int prefixEnd = to.lastIndexOf(from);
        if (prefixEnd == -1) { // rule of form +:a=>b, but we don't support such mapping
          throw new VcsException(String.format(agentCheckoutRulesErrorMessage, rule));
        }
        if (!ignorePostfixInRules && to.length() != prefixEnd + from.length()) {
          /* rule of form +:a=>b/a/c, but we don't support mapping with postfix
          teamcity.internal.git.agent.ignoreCheckoutRulesPostfixCheck turns off this verification but checking out on the agent side will have unpredictable behavior; */
          throw new VcsException(String.format(agentCheckoutRulesErrorMessage, rule));
        }
        String prefix = to.substring(0, prefixEnd);
        if (!prefix.endsWith("/")) { //rule of form +:a=>ab, but we don't support such mapping
          throw new VcsException(String.format(agentCheckoutRulesErrorMessage, rule));
        }
        prefix = prefix.substring(0, prefix.length() - 1);

        targetDir = assignTargetDir(targetDir, prefix, rule, previousRule);
      }

      previousRule = rule;
    }
    if (targetDir == null)
      return "";
    return targetDir;
  }

  private static String assignTargetDir(String currectTargetDir, String newTargetDir, IncludeRule currentRule, IncludeRule prevRule) throws VcsException{
    if (currectTargetDir == null)
      return newTargetDir;
    else if (!currectTargetDir.equals(newTargetDir))
      throw new VcsException(String.format(agentCheckoutRulesErrorMessage, currentRule)); //prevRul is not null because currentTargetDir is set
    return currectTargetDir;
  }
}
