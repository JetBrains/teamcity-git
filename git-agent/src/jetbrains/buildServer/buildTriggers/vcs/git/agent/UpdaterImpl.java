/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Branches;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Refs;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.*;

public class UpdaterImpl implements Updater {

  private final static Logger LOG = Logger.getLogger(UpdaterImpl.class);
  /** Git version which supports --progress option in the fetch command */
  private final static GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);
  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final BuildProgressLogger myLogger;
  protected final AgentPluginConfig myPluginConfig;
  protected final GitFactory myGitFactory;
  protected final File myTargetDirectory;
  protected final String myRevision;
  protected final AgentGitVcsRoot myRoot;
  protected final String myFullBranchName;
  private final AgentRunningBuild myBuild;

  public UpdaterImpl(@NotNull AgentPluginConfig pluginConfig,
                     @NotNull MirrorManager mirrorManager,
                     @NotNull SmartDirectoryCleaner directoryCleaner,
                     @NotNull GitFactory gitFactory,
                     @NotNull AgentRunningBuild build,
                     @NotNull VcsRoot root,
                     @NotNull String version,
                     @NotNull File targetDir) throws VcsException {
    myPluginConfig = pluginConfig;
    myDirectoryCleaner = directoryCleaner;
    myGitFactory = gitFactory;
    myBuild = build;
    myLogger = build.getBuildLogger();
    myRevision = GitUtils.versionRevision(version);
    myTargetDirectory = targetDir;
    myRoot = new AgentGitVcsRoot(mirrorManager, myTargetDirectory, root);
    myFullBranchName = getBranch();
  }


  private String getBranch() {
    String defaultBranchName = GitUtils.expandRef(myRoot.getRef());
    String rootBranchParam = GitUtils.getGitRootBranchParamName(myRoot.getOriginalRoot());
    String customBranch = myBuild.getSharedConfigParameters().get(rootBranchParam);
    return customBranch != null ? customBranch : defaultBranchName;
  }


  public void update() throws VcsException {
    checkAuthMethodIsSupported();
    doUpdate();
  }

  protected void doUpdate() throws VcsException {
    logStartUpdating();
    initGitRepository();
    removeRefLocks(new File(myTargetDirectory, ".git"));
    doFetch();
    updateSources();
  }

  private void logStartUpdating() {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
    LOG.debug("Updating " + myRoot.debugInfo());
  }


  /**
   * Init .git in the target dir
   * @return true if there was no fetch in the target dir before
   * @throws VcsException in teh case of any problems
   */
  private boolean initGitRepository() throws VcsException {
    boolean firstFetch = false;
    if (!new File(myTargetDirectory, ".git").exists()) {
      initDirectory();
      firstFetch = true;
    } else {
      String remoteUrl = getRemoteUrl();
      if (!remoteUrl.equals(myRoot.getRepositoryFetchURL().toString())) {
        initDirectory();
        firstFetch = true;
      } else {
        try {
          setupMirrors();
        } catch (Exception e) {
          LOG.warn("Do clean checkout due to errors while configure use of local mirrors", e);
          initDirectory();
          firstFetch = true;
        }
      }
    }
    return firstFetch;
  }


  protected void setupMirrors() throws VcsException {
    if (isRepositoryUseLocalMirror())
      setNotUseLocalMirror();
  }


  private void updateSources() throws VcsException {
    GitFacade git = myGitFactory.create(myTargetDirectory);
    boolean branchChanged = false;
    removeIndexLock();
    if (isRegularBranch(myFullBranchName)) {
      String branchName = getShortBranchName(myFullBranchName);
      Branches branches = git.branch().call();
      if (branches.isCurrentBranch(branchName)) {
        myLogger.message("Resetting " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
        removeIndexLock();
        try {
          git.reset().setHard(true).setRevision(myRevision).call();
        } catch (GitIndexCorruptedException e) {
          File gitIndex = e.getGitIndex();
          myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat git reset");
          FileUtil.delete(gitIndex);
          git.reset().setHard(true).setRevision(myRevision).call();
        }
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
        myLogger.message("Checking out branch " + myFullBranchName + " in " + myRoot.getName() + " in " + myTargetDirectory + " with revision " + myRevision);
        git.checkout().setForce(true).setBranch(branchName).call();
      }
    } else if (isTag(myFullBranchName)) {
      String shortName = myFullBranchName.substring("refs/tags/".length());
      git.checkout().setForce(true).setBranch(shortName).call();
      branchChanged = true;
    } else {
      myLogger.message("Resetting " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
      git.checkout().setForce(true).setBranch(myRevision).call();
      branchChanged = true;
    }

    doClean(branchChanged);
    if (myRoot.isCheckoutSubmodules()) {
      checkoutSubmodules(myTargetDirectory);
    }
  }


  private void checkoutSubmodules(@NotNull final File repositoryDir) throws VcsException {
    File gitmodules = new File(repositoryDir, ".gitmodules");
    if (gitmodules.exists()) {
      myLogger.message("Checkout submodules in " + repositoryDir);
      GitFacade git = myGitFactory.create(repositoryDir);
      git.submoduleInit().call();
      git.submoduleSync().call();
      git.submoduleUpdate()
        .setAuthSettings(myRoot.getAuthSettings())
        .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
        .setTimeout(SILENT_TIMEOUT)
        .call();

      if (recursiveSubmoduleCheckout()) {
        try {
          String gitmodulesContents = FileUtil.readText(gitmodules);
          Config config = new Config();
          config.fromText(gitmodulesContents);

          Set<String> submodules = config.getSubsections("submodule");
          for (String submoduleName : submodules) {
            String submodulePath = config.getString("submodule", submoduleName, "path");
            checkoutSubmodules(new File(repositoryDir, submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator))));
          }
        } catch (IOException e) {
          throw new VcsException("Error while reading " + gitmodules, e);
        } catch (ConfigInvalidException e) {
          throw new VcsException("Error while parsing " + gitmodules, e);
        }
      }
    }
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
    }
  }


  private void setNotUseLocalMirror() throws VcsException {
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


  private String getRemoteUrl() {
    try {
      return myGitFactory.create(myTargetDirectory).getConfig().setPropertyName("remote.origin.url").call();
    } catch (VcsException e) {
      LOG.debug("Failed to read property", e);
      return "";
    }
  }


  protected boolean isRepositoryUseLocalMirror() throws VcsException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      return !config.getSubsections("url").isEmpty();
    } catch (IOException e) {
      String msg = "Error while reading config file in repository " + myTargetDirectory.getAbsolutePath();
      LOG.error(msg, e);
      throw new VcsException(msg, e);
    } finally {
      if (r != null)
        r.close();
    }
  }


  @Nullable
  protected Ref getRef(@NotNull File repositoryDir, @NotNull String ref) {
    Map<String, Ref> refs = myGitFactory.create(repositoryDir).showRef().setPattern(ref).call();
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
    Ref remoteRef = getRef(myTargetDirectory, GitUtils.createRemoteRef(myFullBranchName));
    if (!fetchRequired && hasRevision(myTargetDirectory, myRevision) && remoteRef != null)
      return;
    myLogger.message("Commit '" + myRevision + "' is not found in repository. Running 'git fetch'...");
    fetchDefaultBranch();
    if (hasRevision(myTargetDirectory, myRevision))
      return;
    fetchAllBranches();
    if (hasRevision(myTargetDirectory, myRevision))
      return;
    throw new VcsException("Cannot find commit " + myRevision);
  }


  private void fetchDefaultBranch() throws VcsException {
    fetch(myTargetDirectory, getRefspecForFetch(), false);
  }

  private String getRefspecForFetch() {
    if (isRegularBranch(myFullBranchName) || isTag(myFullBranchName))
      return "+" + myFullBranchName + ":" + GitUtils.createRemoteRef(myFullBranchName);
    return myFullBranchName;
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

    FetchCommand fetch = myGitFactory.create(repositoryDir).fetch()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .setTimeout(timeout)
      .setRefspec(refspec);

    if (silent)
      fetch.setQuite(true);
    else
      fetch.setShowProgress(true);

    if (shallowClone)
      fetch.setDepth(1);

    try {
      fetch.call();
    } catch (GitIndexCorruptedException e) {
      File gitIndex = e.getGitIndex();
      myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat git fetch");
      FileUtil.delete(gitIndex);
      fetch.call();
    } catch (GitExecTimeout e) {
      if (!silent) {
        myLogger.error("No output from git during " + timeout + " seconds. Try increasing idle timeout by setting parameter '"
                       + PluginConfigImpl.IDLE_TIMEOUT +
                       "' either in build or in agent configuration.");
      }
      throw e;
    }
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
    if ("git".equals(myRoot.getRepositoryFetchURL().getScheme()))
      return;//anonymous protocol, don't check anything
    AuthSettings authSettings = myRoot.getAuthSettings();
    switch (authSettings.getAuthMethod()) {
      case PASSWORD:
        if ("http".equals(myRoot.getRepositoryFetchURL().getScheme()) ||
            "https".equals(myRoot.getRepositoryFetchURL().getScheme())) {
          GitVersion actualVersion = myPluginConfig.getGitVersion();
          GitVersion requiredVersion = getMinVersionForHttpAuth();
          if (actualVersion.isLessThan(requiredVersion)) {
            throw new VcsException("Password authentication requires git " + requiredVersion +
                    ", found git version is " + actualVersion +
                    ". Upgrade git or use different authentication method.");
          }
        } else {
          throw new VcsException("TeamCity doesn't support authentication method '" +
                  myRoot.getAuthSettings().getAuthMethod().uiName() +
                  "' with agent checkout and non-http protocols. Please use different authentication method.");
        }
        break;
      case PRIVATE_KEY_FILE:
        throw new VcsException("TeamCity doesn't support authentication method '" +
                myRoot.getAuthSettings().getAuthMethod().uiName() +
                "' with agent checkout. Please use different authentication method.");
    }
  }

  @NotNull
  private GitVersion getMinVersionForHttpAuth() {
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
  void initDirectory() throws VcsException {
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
    myGitFactory.create(myRoot.getLocalRepositoryDir())
      .addRemote()
      .setName("origin")
      .setUrl(myRoot.getRepositoryFetchURL().toString())
      .call();
    URIish url = myRoot.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(myRoot.getRepositoryFetchURL().toString())) {
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("remote.origin.pushurl").setValue(pushUrl).call();
    }
    postInit();
  }


  protected void postInit() throws VcsException {
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
    Refs localRefs = new Refs(git.showRef().call());
    if (localRefs.isEmpty())
      return false;
    Refs remoteRefs = new Refs(git.lsRemote().setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .call());
    for (Ref localRef : localRefs.list()) {
      if (remoteRefs.isOutdated(localRef)) {
        git.updateRef().setRef(localRef.getName()).delete().call();
        outdatedRefsRemoved = true;
      }
    }
    return outdatedRefsRemoved;
  }
}
