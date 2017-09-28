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

import com.intellij.openapi.util.Trinity;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.RefImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitOutdatedIndexException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.*;

public class UpdaterImpl implements Updater {

  private final static Logger LOG = Logger.getLogger(UpdaterImpl.class);
  /** Git version which supports --progress option in the fetch command */
  private final static GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);
  //--force option in git submodule update introduced in 1.7.6
  private final static GitVersion GIT_WITH_FORCE_SUBMODULE_UPDATE = new GitVersion(1, 7, 6);
  public final static GitVersion GIT_WITH_SPARSE_CHECKOUT = new GitVersion(1, 7, 4);
  public final static GitVersion BROKEN_SPARSE_CHECKOUT = new GitVersion(2, 7, 0);
  /**
   * Git version supporting an empty credential helper - the only way to disable system/global/local cred helper
   */
  public final static GitVersion EMPTY_CRED_HELPER = new GitVersion(2, 9, 0);
  /** Git version supporting [credential] section in config (the first version including a6fc9fd3f4b42cd97b5262026e18bd451c28ee3c) */
  public final static GitVersion CREDENTIALS_SECTION_VERSION = new GitVersion(1, 7, 10);

  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours

  protected final FS myFS;
  private final SmartDirectoryCleaner myDirectoryCleaner;
  protected final BuildProgressLogger myLogger;
  protected final AgentPluginConfig myPluginConfig;
  protected final GitFactory myGitFactory;
  protected final File myTargetDirectory;
  protected final String myRevision;
  protected final AgentGitVcsRoot myRoot;
  protected final String myFullBranchName;
  protected final AgentRunningBuild myBuild;
  private final CheckoutRules myRules;
  private final CheckoutMode myCheckoutMode;
  protected final MirrorManager myMirrorManager;
  //remote repository refs, stored in field in order to not run 'git ls-remote' command twice
  private Refs myRemoteRefs;

  public UpdaterImpl(@NotNull FS fs,
                     @NotNull AgentPluginConfig pluginConfig,
                     @NotNull MirrorManager mirrorManager,
                     @NotNull SmartDirectoryCleaner directoryCleaner,
                     @NotNull GitFactory gitFactory,
                     @NotNull AgentRunningBuild build,
                     @NotNull VcsRoot root,
                     @NotNull String version,
                     @NotNull File targetDir,
                     @NotNull CheckoutRules rules,
                     @NotNull CheckoutMode checkoutMode) throws VcsException {
    myFS = fs;
    myPluginConfig = pluginConfig;
    myDirectoryCleaner = directoryCleaner;
    myGitFactory = gitFactory;
    myBuild = build;
    myLogger = build.getBuildLogger();
    myRevision = GitUtils.versionRevision(version);
    myTargetDirectory = targetDir;
    myRoot = new AgentGitVcsRoot(mirrorManager, myTargetDirectory, root);
    myFullBranchName = getBranch();
    myRules = rules;
    myCheckoutMode = checkoutMode;
    myMirrorManager = mirrorManager;
  }


  private String getBranch() {
    String defaultBranchName = GitUtils.expandRef(myRoot.getRef());
    String rootBranchParam = GitUtils.getGitRootBranchParamName(myRoot.getOriginalRoot());
    String customBranch = myBuild.getSharedConfigParameters().get(rootBranchParam);
    return customBranch != null ? customBranch : defaultBranchName;
  }


  public void update() throws VcsException {
    String msg = "Git version: " + myPluginConfig.getGitVersion();
    myLogger.message(msg);
    LOG.info(msg);
    checkAuthMethodIsSupported();
    doUpdate();
  }

  protected void doUpdate() throws VcsException {
    String message = "Update checkout directory (" + myTargetDirectory.getAbsolutePath() + ")";
    myLogger.activityStarted(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    try {
      logStartUpdating();
      initGitRepository();
      removeRefLocks(new File(myTargetDirectory, ".git"));
      doFetch();
      updateSources();
    } finally {
      myLogger.activityFinished(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    }
  }

  private void logStartUpdating() {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
    LOG.debug("Updating " + myRoot.debugInfo());
  }


  private void initGitRepository() throws VcsException {
    if (!new File(myTargetDirectory, ".git").exists()) {
      initDirectory();
    } else {
      try {
        configureRemoteUrl(new File(myTargetDirectory, ".git"));
        setupExistingRepository();
        configureSparseCheckout();
      } catch (Exception e) {
        LOG.warn("Do clean checkout due to errors while configure use of local mirrors", e);
        initDirectory();
      }
    }
    removeOrphanedIdxFiles(new File(myTargetDirectory, ".git"));
  }

  protected void setupNewRepository() throws VcsException {
  }


  protected void setupExistingRepository() throws VcsException {
    removeUrlSections();
    removeLfsStorage();
    disableAlternates();
  }


  private void updateSources() throws VcsException {
    final GitFacade git = myGitFactory.create(myTargetDirectory);
    boolean branchChanged = false;
    removeIndexLock();
    if (isRegularBranch(myFullBranchName)) {
      String branchName = getShortBranchName(myFullBranchName);
      Branches branches = git.listBranches();
      if (branches.isCurrentBranch(branchName)) {
        removeIndexLock();
        runAndFixIndexErrors(git, new VcsCommand() {
          @Override
          public void call() throws VcsException {
            reset(git).setHard(true).setRevision(myRevision).call();
          }
        });
        git.setUpstream(branchName, GitUtils.createRemoteRef(myFullBranchName)).call();
      } else {
        branchChanged = true;
        if (!branches.contains(branchName)) {
          git.createBranch()
            .setName(branchName)
            .setStartPoint(GitUtils.createRemoteRef(myFullBranchName))
            .setTrack(true)
            .call();
        }
        git.updateRef().setRef(myFullBranchName).setRevision(myRevision).call();
        final String finalBranchName = branchName;
        runAndFixIndexErrors(git, new VcsCommand() {
          @Override
          public void call() throws VcsException {
            checkout(git).setForce(true).setBranch(finalBranchName).setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds()).call();
          }
        });
        if (branches.contains(branchName)) {
          git.setUpstream(branchName, GitUtils.createRemoteRef(myFullBranchName)).call();
        }
      }
    } else if (isTag(myFullBranchName)) {
      final String shortName = myFullBranchName.substring("refs/tags/".length());
      runAndFixIndexErrors(git, new VcsCommand() {
        @Override
        public void call() throws VcsException {
          checkout(git).setForce(true).setBranch(shortName).setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds()).call();
        }
      });
      Ref tag = getRef(myTargetDirectory, myFullBranchName);
      if (tag != null && !tag.getObjectId().name().equals(myRevision)) {
        runAndFixIndexErrors(git, new VcsCommand() {
          @Override
          public void call() throws VcsException {
            checkout(git).setBranch(myRevision).setForce(true).setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds()).call();
          }
        });
      }
      branchChanged = true;
    } else {
      runAndFixIndexErrors(git, new VcsCommand() {
        @Override
        public void call() throws VcsException {
          checkout(git).setForce(true).setBranch(myRevision).setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds()).call();
        }
      });
      branchChanged = true;
    }

    doClean(branchChanged);
    if (myRoot.isCheckoutSubmodules()) {
      checkoutSubmodules(myTargetDirectory);
    }
  }


  private void runAndFixIndexErrors(@NotNull GitFacade git, @NotNull VcsCommand cmd) throws VcsException {
    try {
      cmd.call();
    } catch (GitIndexCorruptedException e) {
      File gitIndex = e.getGitIndex();
      myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat the command");
      FileUtil.delete(gitIndex);
      cmd.call();
    } catch (GitOutdatedIndexException e) {
      myLogger.message("Refresh outdated git index and repeat the command");
      updateIndex(git).reallyRefresh(true).quiet(true).call();
      cmd.call();
    } catch (Exception e) {
      if (e instanceof VcsException)
        throw (VcsException) e;
      throw new VcsException(e);
    }
  }


  @NotNull
  private UpdateIndexCommand updateIndex(final GitFacade git) {
    UpdateIndexCommand result = git.updateIndex()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
    configureLFS(result);
    return result;
  }


  @NotNull
  private ResetCommand reset(final GitFacade git) {
    ResetCommand result = git.reset()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
    configureLFS(result);
    return result;
  }

  @NotNull
  private CheckoutCommand checkout(final GitFacade git) {
    CheckoutCommand result = git.checkout()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
    configureLFS(result);
    return result;
  }

  private void checkoutSubmodules(@NotNull final File repositoryDir) throws VcsException {
    File dotGitModules = new File(repositoryDir, ".gitmodules");
    try {
      Config gitModules = readGitModules(dotGitModules);
      if (gitModules == null)
        return;

      myLogger.message("Checkout submodules in " + repositoryDir);
      GitFacade git = myGitFactory.create(repositoryDir);
      git.submoduleInit().call();
      git.submoduleSync().call();

      addSubmoduleUsernames(repositoryDir, gitModules);

      long start = System.currentTimeMillis();
      SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate()
        .setAuthSettings(myRoot.getAuthSettings())
        .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
        .setTimeout(SILENT_TIMEOUT)
        .setForce(isForceUpdateSupported());
      configureLFS(submoduleUpdate);
      submoduleUpdate.call();

      if (recursiveSubmoduleCheckout()) {
        for (String submodulePath : getSubmodulePaths(gitModules)) {
          checkoutSubmodules(new File(repositoryDir, submodulePath));
        }
      }
      Loggers.VCS.info("Submodules update in " + repositoryDir.getAbsolutePath() + " is finished in " +
                       (System.currentTimeMillis() - start) + " ms");

    } catch (IOException e) {
      Loggers.VCS.error("Submodules checkout failed", e);
      throw new VcsException("Submodules checkout failed", e);
    } catch (ConfigInvalidException e) {
      Loggers.VCS.error("Submodules checkout failed", e);
      throw new VcsException("Submodules checkout failed", e);
    }
  }


  private boolean isForceUpdateSupported() {
    return !GIT_WITH_FORCE_SUBMODULE_UPDATE.isGreaterThan(myPluginConfig.getGitVersion());
  }


  private void addSubmoduleUsernames(@NotNull File repositoryDir, @NotNull Config gitModules)
    throws IOException, ConfigInvalidException, VcsException {
    if (!myPluginConfig.isUseMainRepoUserForSubmodules())
      return;

    Loggers.VCS.info("Update submodules credentials");

    AuthSettings auth = myRoot.getAuthSettings();
    final String userName = auth.getUserName();
    if (userName == null) {
      Loggers.VCS.info("Username is not specified in the main VCS root settings, skip updating credentials");
      return;
    }

    Repository r = new RepositoryBuilder().setBare().setGitDir(getGitDir(repositoryDir)).build();
    StoredConfig gitConfig = r.getConfig();

    Set<String> submodules = gitModules.getSubsections("submodule");
    if (submodules.isEmpty()) {
      Loggers.VCS.info("No submodule sections found in " + new File(repositoryDir, ".gitmodules").getCanonicalPath()
                       + ", skip updating credentials");
      return;
    }
    File modulesDir = new File(r.getDirectory(), Constants.MODULES);
    for (String submoduleName : submodules) {
      String url = gitModules.getString("submodule", submoduleName, "url");
      Loggers.VCS.info("Update credentials for submodule with url " + url);
      if (url == null || !isRequireAuth(url)) {
        Loggers.VCS.info("Url " + url + " does not require authentication, skip updating credentials");
        continue;
      }
      try {
        URIish uri = new URIish(url);
        String updatedUrl = uri.setUser(userName).toASCIIString();
        gitConfig.setString("submodule", submoduleName, "url", updatedUrl);
        String submodulePath = gitModules.getString("submodule", submoduleName, "path");
        if (submodulePath != null && myPluginConfig.isUpdateSubmoduleOriginUrl()) {
          File submoduleDir = new File(modulesDir, submodulePath);
          if (submoduleDir.isDirectory() && new File(submoduleDir, Constants.CONFIG).isFile())
            updateOriginUrl(submoduleDir, updatedUrl);
        }
        Loggers.VCS.debug("Submodule url " + url + " changed to " + updatedUrl);
      } catch (URISyntaxException e) {
        Loggers.VCS.warn("Error while parsing an url " + url + ", skip updating submodule credentials", e);
      } catch (Exception e) {
        Loggers.VCS.warn("Error while updating the '" + submoduleName + "' submodule url", e);
      }
    }
    gitConfig.save();
  }

  private void updateOriginUrl(@NotNull File repoDir, @NotNull String url) throws IOException {
    Repository r = new RepositoryBuilder().setBare().setGitDir(repoDir).build();
    StoredConfig config = r.getConfig();
    config.setString("remote", "origin", "url", url);
    config.save();
  }


  @Nullable
  private Config readGitModules(@NotNull File dotGitModules) throws IOException, ConfigInvalidException {
    if (!dotGitModules.exists())
      return null;
    String content = FileUtil.readText(dotGitModules);
    Config config = new Config();
    config.fromText(content);
    return config;
  }


  private boolean isRequireAuth(@NotNull String url) {
    try {
      URIish uri = new URIish(url);
      String scheme = uri.getScheme();
      if (scheme == null || "git".equals(scheme)) //no auth for anonymous protocol and for local repositories
        return false;
      String user = uri.getUser();
      if (user != null) //respect a user specified in config
        return false;
      return true;
    } catch (URISyntaxException e) {
      return false;
    }
  }


  private Set<String> getSubmodulePaths(@NotNull Config config) {
    Set<String> paths = new HashSet<String>();
    Set<String> submodules = config.getSubsections("submodule");
    for (String submoduleName : submodules) {
      String submodulePath = config.getString("submodule", submoduleName, "path");
      paths.add(submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator)));
    }
    return paths;
  }

  private boolean recursiveSubmoduleCheckout() {
    return SubmodulesCheckoutPolicy.CHECKOUT.equals(myRoot.getSubmodulesCheckoutPolicy()) ||
           SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS.equals(myRoot.getSubmodulesCheckoutPolicy());
  }


  private void doClean(boolean branchChanged) throws VcsException {
    if (myRoot.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        branchChanged && myRoot.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE) {
      myLogger.message("Cleaning " + myRoot.getName() + " in " + myTargetDirectory + " the file set " + myRoot.getCleanFilesPolicy());
      myGitFactory.create(myTargetDirectory).clean().setCleanPolicy(myRoot.getCleanFilesPolicy()).call();

      if (myRoot.isCheckoutSubmodules())
        cleanSubmodules(myTargetDirectory);
    }
  }


  private void cleanSubmodules(@NotNull File repositoryDir) throws VcsException {
    File dotGitModules = new File(repositoryDir, ".gitmodules");
    Config gitModules;
    try {
      gitModules = readGitModules(dotGitModules);
    } catch (Exception e) {
      Loggers.VCS.error("Error while reading " + dotGitModules.getAbsolutePath() + ": " + e.getMessage());
      throw new VcsException("Error while reading " + dotGitModules.getAbsolutePath(), e);
    }

    if (gitModules == null)
      return;

    for (String submodulePath : getSubmodulePaths(gitModules)) {
      File submoduleDir = new File(repositoryDir, submodulePath);
      try {
        myLogger.message("Cleaning files in " + submoduleDir + " the file set " + myRoot.getCleanFilesPolicy());
        myGitFactory.create(submoduleDir).clean().setCleanPolicy(myRoot.getCleanFilesPolicy()).call();
      } catch (Exception e) {
        Loggers.VCS.error("Error while cleaning files in " + submoduleDir.getAbsolutePath(), e);
      }
      if (recursiveSubmoduleCheckout())
        cleanSubmodules(submoduleDir);
    }
  }


  protected void removeUrlSections() throws VcsException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      Set<String> urlSubsections = config.getSubsections("url");
      for (String subsection : urlSubsections) {
        config.unsetSection("url", subsection);
      }
      config.save();
    } catch (IOException e) {
      String msg = "Error while remove url.* sections";
      LOG.error(msg, e);
      throw new VcsException(msg, e);
    } finally {
      if (r != null)
        r.close();
    }
  }


  private void removeLfsStorage() throws VcsException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      config.unsetSection("lfs", null);
      config.save();
    } catch (IOException e) {
      String msg = "Error while removing lfs.storage section";
      LOG.error(msg, e);
      throw new VcsException(msg, e);
    } finally {
      if (r != null)
        r.close();
    }
  }


  protected void disableAlternates() {
    FileUtil.delete(new File(myTargetDirectory, ".git" + File.separator + "objects" + File.separator + "info" + File.separator + "alternates"));
  }


  private String getRemoteUrl() {
    try {
      return myGitFactory.create(myTargetDirectory).getConfig().setPropertyName("remote.origin.url").call();
    } catch (VcsException e) {
      LOG.debug("Failed to read property", e);
      return "";
    }
  }


  @Nullable
  protected Ref getRef(@NotNull File repositoryDir, @NotNull String ref) {
    Map<String, Ref> refs = myGitFactory.create(repositoryDir).showRef().setPattern(ref).call().getValidRefs();
    return refs.isEmpty() ? null : refs.get(ref);
  }


  /**
   * If some git process crashed in this repository earlier it can leave lock files for index.
   * This method delete such lock file if it exists (with warning message), otherwise git operation will fail.
   */
  private void removeIndexLock() {
    File indexLock = new File(myTargetDirectory, ".git" + File.separator + "index.lock");
    if (indexLock.exists()) {
      myLogger.warning("The .git/index.lock file exists. This probably means a git process crashed in this repository earlier. Deleting lock file");
      FileUtil.delete(indexLock);
    }
  }


  private void doFetch() throws VcsException {
    boolean outdatedRefsFound = removeOutdatedRefs(myTargetDirectory);
    ensureCommitLoaded(outdatedRefsFound);
  }


  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    fetchFromOriginalRepository(fetchRequired);
  }


  protected void fetchFromOriginalRepository(boolean fetchRequired) throws VcsException {
    if (myPluginConfig.isFetchAllHeads()) {
      String msg = getForcedHeadsFetchMessage();
      LOG.info(msg);
      myLogger.message(msg);

      fetchAllBranches();
      if (!myFullBranchName.startsWith("refs/heads/")) {
        Ref remoteRef = getRef(myTargetDirectory, GitUtils.createRemoteRef(myFullBranchName));
        if (fetchRequired || remoteRef == null || !myRevision.equals(remoteRef.getObjectId().name()) || !hasRevision(myTargetDirectory, myRevision))
          fetchDefaultBranch();
      }
    } else {
      Ref remoteRef = getRef(myTargetDirectory, GitUtils.createRemoteRef(myFullBranchName));
      if (!fetchRequired && remoteRef != null && myRevision.equals(remoteRef.getObjectId().name()) && hasRevision(myTargetDirectory, myRevision))
        return;
      myLogger.message("Commit '" + myRevision + "' is not found in local clone. Running 'git fetch'...");
      fetchDefaultBranch();
      if (hasRevision(myTargetDirectory, myRevision))
        return;
      myLogger.message("Commit still not found after fetching main branch. Fetching more branches.");
      fetchAllBranches();
    }
    if (hasRevision(myTargetDirectory, myRevision))
      return;
    throw new VcsException("Cannot find commit " + myRevision);
  }


  protected String getForcedHeadsFetchMessage() {
    return "Forced fetch of all heads (" + PluginConfigImpl.FETCH_ALL_HEADS + "=" + myBuild.getSharedConfigParameters().get(PluginConfigImpl.FETCH_ALL_HEADS) + ")";
  }


  private void fetchDefaultBranch() throws VcsException {
    fetch(myTargetDirectory, getRefspecForFetch(), false);
  }

  private String getRefspecForFetch() {
    return "+" + myFullBranchName + ":" + GitUtils.createRemoteRef(myFullBranchName);
  }

  private void fetchAllBranches() throws VcsException {
    fetch(myTargetDirectory, "+refs/heads/*:refs/remotes/origin/*", false);
  }

  protected boolean hasRevision(@NotNull File repositoryDir, @NotNull String revision) {
    return getRevision(repositoryDir, revision) != null;
  }

  private String getRevision(@NotNull File repositoryDir, @NotNull String revision) {
    return myGitFactory.create(repositoryDir).log()
      .setCommitsNumber(1)
      .setPrettyFormat("%H%x20%s")
      .setStartPoint(revision)
      .call();
  }

  protected void fetch(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone) throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);

    try {
      getFetch(repositoryDir, refspec, shallowClone, silent, timeout).call();
    } catch (GitIndexCorruptedException e) {
      File gitIndex = e.getGitIndex();
      myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat git fetch");
      FileUtil.delete(gitIndex);
      getFetch(repositoryDir, refspec, shallowClone, silent, timeout).call();
    } catch (GitExecTimeout e) {
      if (!silent) {
        myLogger.error("No output from git during " + timeout + " seconds. Try increasing idle timeout by setting parameter '"
                       + PluginConfigImpl.IDLE_TIMEOUT +
                       "' either in build or in agent configuration.");
      }
      throw e;
    }
  }

  @NotNull
  private FetchCommand getFetch(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone, boolean silent, int timeout) {
    FetchCommand result = myGitFactory.create(repositoryDir).fetch()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .setTimeout(timeout)
      .setRefspec(refspec)
      .setFetchTags(myPluginConfig.isFetchTags());

    if (silent)
      result.setQuite(true);
    else
      result.setShowProgress(true);

    if (shallowClone)
      result.setDepth(1);

    return result;
  }

  protected void removeRefLocks(@NotNull File dotGit) {
    File refs = new File(dotGit, "refs");
    if (!refs.isDirectory())
      return;
    Collection<File> locks = FileUtil.findFiles(new FileFilter() {
      public boolean accept(File f) {
        return f.isFile() && f.getName().endsWith(".lock");
      }
    }, refs);
    for (File lock : locks) {
      LOG.info("Remove a lock file " + lock.getAbsolutePath());
      FileUtil.delete(lock);
    }
  }

  private boolean isSilentFetch() {
    GitVersion version = myPluginConfig.getGitVersion();
    return version.isLessThan(GIT_WITH_PROGRESS_VERSION);
  }

  private int getTimeout(boolean silentFetch) {
    if (silentFetch)
      return SILENT_TIMEOUT;
    else
      return myPluginConfig.getIdleTimeoutSeconds();
  }


  private void checkAuthMethodIsSupported() throws VcsException {
    checkAuthMethodIsSupported(myRoot, myPluginConfig);
  }


  static void checkAuthMethodIsSupported(@NotNull GitVcsRoot root, @NotNull AgentPluginConfig config) throws VcsException {
    if ("git".equals(root.getRepositoryFetchURL().getScheme()))
      return;//anonymous protocol, don't check anything
    AuthSettings authSettings = root.getAuthSettings();
    switch (authSettings.getAuthMethod()) {
      case PASSWORD:
        if ("http".equals(root.getRepositoryFetchURL().getScheme()) ||
            "https".equals(root.getRepositoryFetchURL().getScheme())) {
          GitVersion actualVersion = config.getGitVersion();
          GitVersion requiredVersion = getMinVersionForHttpAuth();
          if (actualVersion.isLessThan(requiredVersion)) {
            throw new VcsException("Password authentication requires git " + requiredVersion +
                                   ", found git version is " + actualVersion +
                                   ". Upgrade git or use different authentication method.");
          }
        } else {
          throw new VcsException("TeamCity doesn't support authentication method '" +
                                 root.getAuthSettings().getAuthMethod().uiName() +
                                 "' with agent checkout and non-http protocols. Please use different authentication method.");
        }
        break;
      case PRIVATE_KEY_FILE:
        throw new VcsException("TeamCity doesn't support authentication method '" +
                               root.getAuthSettings().getAuthMethod().uiName() +
                               "' with agent checkout. Please use different authentication method.");
    }
  }

  @NotNull
  private static GitVersion getMinVersionForHttpAuth() {
    //core.askpass parameter was added in 1.7.1, but
    //experiments show that it works only in 1.7.3 on linux
    //and msysgit 1.7.3.1-preview20101002.
    return new GitVersion(1, 7, 3);
  }

  /**
   * Clean and init directory and configure remote origin
   *
   * @throws VcsException if there are problems with initializing the directory
   */
  private void initDirectory() throws VcsException {
    BuildDirectoryCleanerCallback c = new BuildDirectoryCleanerCallback(myLogger, LOG);
    myDirectoryCleaner.cleanFolder(myTargetDirectory, c);
    //noinspection ResultOfMethodCallIgnored
    myTargetDirectory.mkdirs();
    if (c.isHasErrors()) {
      throw new VcsException("Unable to clean directory " + myTargetDirectory + " for VCS root " + myRoot.getName());
    }
    myLogger.message("The .git directory is missing in '" + myTargetDirectory + "'. Running 'git init'...");
    myGitFactory.create(myTargetDirectory).init().call();
    validateUrls();
    configureRemoteUrl(new File(myTargetDirectory, ".git"));

    URIish fetchUrl = myRoot.getRepositoryFetchURL();
    URIish url = myRoot.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(fetchUrl.toString())) {
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("remote.origin.pushurl").setValue(pushUrl).call();
    }
    setupNewRepository();
    configureSparseCheckout();
  }


  void configureRemoteUrl(@NotNull File gitDir) throws VcsException {
    RemoteRepositoryConfigurator cfg = new RemoteRepositoryConfigurator();
    cfg.setGitDir(gitDir);
    cfg.setExcludeUsernameFromHttpUrls(myPluginConfig.isExcludeUsernameFromHttpUrl() && !myPluginConfig.getGitVersion().isLessThan(UpdaterImpl.CREDENTIALS_SECTION_VERSION));
    cfg.configure(myRoot);
  }


  private void configureSparseCheckout() throws VcsException {
    if (myCheckoutMode == CheckoutMode.SPARSE_CHECKOUT) {
      setupSparseCheckout();
    } else {
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("false").call();
    }
  }

  private void setupSparseCheckout() throws VcsException {
    myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("true").call();
    File sparseCheckout = new File(myTargetDirectory, ".git/info/sparse-checkout");
    boolean hasIncludeRules = false;
    StringBuilder sparseCheckoutContent = new StringBuilder();
    for (IncludeRule rule : myRules.getIncludeRules()) {
      if (isEmpty(rule.getFrom())) {
        sparseCheckoutContent.append("/*\n");
      } else {
        sparseCheckoutContent.append("/").append(rule.getFrom()).append("\n");
      }
      hasIncludeRules = true;
    }
    if (!hasIncludeRules) {
      sparseCheckoutContent.append("/*\n");
    }
    for (FileRule rule : myRules.getExcludeRules()) {
      sparseCheckoutContent.append("!/").append(rule.getFrom()).append("\n");
    }
    try {
      FileUtil.writeFileAndReportErrors(sparseCheckout, sparseCheckoutContent.toString());
    } catch (IOException e) {
      LOG.warn("Error while writing sparse checkout config, disable sparse checkout", e);
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("false").call();
    }
  }


  private void validateUrls() {
    URIish fetch = myRoot.getRepositoryFetchURL();
    if (isAnonymousGitWithUsername(fetch))
      LOG.warn("Fetch URL '" + fetch.toString() + "' for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, fetch will probably fail");
    URIish push  = myRoot.getRepositoryPushURL();
    if (!fetch.equals(push) && isAnonymousGitWithUsername(push))
      LOG.warn("Push URL '" + push.toString() + "'for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, push will probably fail");
  }


  protected boolean removeOutdatedRefs(@NotNull File workingDir) throws VcsException {
    boolean outdatedRefsRemoved = false;
    GitFacade git = myGitFactory.create(workingDir);
    ShowRefResult showRefResult = git.showRef().call();
    Refs localRefs = new Refs(showRefResult.getValidRefs());
    if (localRefs.isEmpty() && showRefResult.getInvalidRefs().isEmpty())
      return false;
    for (String invalidRef : showRefResult.getInvalidRefs()) {
      git.updateRef().setRef(invalidRef).delete().call();
      outdatedRefsRemoved = true;
    }
    final Refs remoteRefs;
    try {
      remoteRefs = getRemoteRefs(workingDir);
    } catch (VcsException e) {
      if (CommandUtil.isCanceledError(e))
        throw e;
      String msg = "Failed to list remote repository refs, outdated local refs will not be cleaned";
      LOG.warn(msg);
      myLogger.warning(msg);
      return false;
    }
    //We remove both outdated local refs (e.g. refs/heads/topic) and outdated remote
    //tracking branches (refs/remote/origin/topic), while git remote origin prune
    //removes only the latter. We need that because in some cases git cannot handle
    //rename of the branch (TW-28735).
    for (Ref localRef : localRefs.list()) {
      Ref correspondingRemoteRef = createCorrespondingRemoteRef(localRef);
      if (remoteRefs.isOutdated(correspondingRemoteRef)) {
        git.updateRef().setRef(localRef.getName()).delete().call();
        outdatedRefsRemoved = true;
      }
    }
    return outdatedRefsRemoved;
  }


  @NotNull
  private Refs getRemoteRefs(@NotNull File workingDir) throws VcsException {
    if (myRemoteRefs != null)
      return myRemoteRefs;
    GitFacade git = myGitFactory.create(workingDir);
    myRemoteRefs = new Refs(git.lsRemote().setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .call());
    return myRemoteRefs;
  }


  private boolean isRemoteTrackingBranch(@NotNull Ref localRef) {
    return localRef.getName().startsWith("refs/remotes/origin");
  }

  @NotNull
  private Ref createCorrespondingRemoteRef(@NotNull Ref localRef) {
    if (!isRemoteTrackingBranch(localRef))
      return localRef;
    return new RefImpl("refs/heads" + localRef.getName().substring("refs/remotes/origin".length()),
                       localRef.getObjectId().name());
  }


  private void configureLFS(@NotNull BaseCommand command) {
    if (!myPluginConfig.isProvideCredHelper())
      return;
    Trinity<String, String, String> lfsAuth = getLfsAuth();
    if (lfsAuth == null)
      return;
    File credentialsHelper = null;
    try {
      ScriptGen scriptGen = myGitFactory.create(new File(".")).getScriptGen();
      final File credHelper = scriptGen.generateCredentialsHelper();
      credentialsHelper = credHelper;
      if (!myPluginConfig.getGitVersion().isLessThan(UpdaterImpl.EMPTY_CRED_HELPER)) {
        //Specify an empty helper if it is supported in order to disable
        //helpers in system-global-local chain. If empty helper is not supported,
        //then the only workaround is to disable helpers manually in config files.
        command.addConfig("credential.helper", "");
      }
      String path = credHelper.getCanonicalPath();
      path = path.replaceAll("\\\\", "/");
      command.addConfig("credential.helper", path);
      CredentialsHelperConfig config = new CredentialsHelperConfig();
      config.addCredentials(lfsAuth.first, lfsAuth.second, lfsAuth.third);
      config.setMatchAllUrls(myPluginConfig.isCredHelperMatchesAllUrls());
      for (Map.Entry<String, String> e : config.getEnv().entrySet()) {
        command.setEnv(e.getKey(), e.getValue());
      }
      if (myPluginConfig.isCleanCredHelperScript()) {
        command.addPostAction(new Runnable() {
          @Override
          public void run() {
            FileUtil.delete(credHelper);
          }
        });
      }
    } catch (Exception e) {
      if (credentialsHelper != null)
        FileUtil.delete(credentialsHelper);
    }
  }


  //returns (url, name, pass) for lfs or null if no authentication is required or
  //root doesn't use http(s)
  @Nullable
  private Trinity<String, String, String> getLfsAuth() {
    try {
      URIish uri = new URIish(myRoot.getRepositoryFetchURL().toString());
      String scheme = uri.getScheme();
      if (myRoot.getAuthSettings().getAuthMethod() == AuthenticationMethod.PASSWORD &&
          ("http".equals(scheme) || "https".equals(scheme))) {
        String lfsUrl = uri.setPass("").setUser("").toASCIIString();
        if (lfsUrl.endsWith(".git")) {
          lfsUrl += "/info/lfs";
        } else {
          lfsUrl += lfsUrl.endsWith("/") ? ".git/info/lfs" : "/.git/info/lfs";
        }
        return Trinity.create(lfsUrl, myRoot.getAuthSettings().getUserName(), myRoot.getAuthSettings().getPassword());
      }
    } catch (Exception e) {
      LOG.debug("Cannot get lfs auth config", e);
    }
    return null;
  }


  private interface VcsCommand {
    void call() throws VcsException;
  }


  /**
   * Removes .idx files which don't have a corresponding .pack file
   * @param ditGitDir git dir
   */
  void removeOrphanedIdxFiles(@NotNull File ditGitDir) {
    if ("false".equals(myBuild.getSharedConfigParameters().get("teamcity.git.removeOrphanedIdxFiles"))) {
      //looks like this logic is always needed, if no problems will be reported we can drop the option
      return;
    }
    File packDir = new File(new File(ditGitDir, "objects"), "pack");
    File[] files = packDir.listFiles();
    if (files == null || files.length == 0)
      return;

    Set<String> packs = new HashSet<String>();
    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".pack")) {
        packs.add(name.substring(0, name.length() - 5));
      }
    }

    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".idx")) {
        if (!packs.contains(name.substring(0, name.length() - 4)))
          FileUtil.delete(f);
      }
    }
  }
}
