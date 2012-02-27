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

import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Tags;
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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isAnonymousGitWithUsername;

public class UpdaterImpl implements Updater {

  private final static Logger LOG = Logger.getLogger(UpdaterImpl.class);
  /** Git version which supports --progress option in the fetch command */
  private final static GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);
  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final BuildProgressLogger myLogger;
  private final AgentPluginConfig myPluginConfig;
  private final GitFactory myGitFactory;
  private final VcsRoot myRoot;
  private final File myTargetDirectory;
  private final String myRevision;
  private final AgentSettings mySettings;

  public UpdaterImpl(@NotNull AgentPluginConfig pluginConfig,
                     @NotNull MirrorManager mirrorManager,
                     @NotNull SmartDirectoryCleaner directoryCleaner,
                     @NotNull GitFactory gitFactory,
                     @NotNull BuildProgressLogger logger,
                     @NotNull VcsRoot root,
                     @NotNull String version,
                     @NotNull File targetDir) throws VcsException {
    myPluginConfig = pluginConfig;
    myDirectoryCleaner = directoryCleaner;
    myGitFactory = gitFactory;
    myLogger = logger;
    myRoot = root;
    myRevision = GitUtils.versionRevision(version);
    myTargetDirectory = targetDir;
    mySettings = new AgentSettings(mirrorManager, myTargetDirectory, root);
  }


  public void update() throws VcsException {
    checkAuthMethodIsSupported();

    if (myPluginConfig.isUseLocalMirrors())
      updateLocalMirror();

    logStartUpdating();

    boolean firstFetch = initGitRepository();
    doFetch(firstFetch);
    updateSources();
  }

  private void logStartUpdating() {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
    LOG.debug("Updating " + mySettings.debugInfo());
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
      if (!remoteUrl.equals(mySettings.getRepositoryFetchURL().toString())) {
        initDirectory();
        firstFetch = true;
      } else {
        try {
          if (myPluginConfig.isUseLocalMirrors() && !isRepositoryUseLocalMirror()) {
            setUseLocalMirror();
          } else if (!myPluginConfig.isUseLocalMirrors() && isRepositoryUseLocalMirror()) {
            setNotUseLocalMirror();
          }
        } catch (Exception e) {
          LOG.warn("Do clean checkout due to errors while configure use of local mirrors", e);
          initDirectory();
          firstFetch = true;
        }
      }
    }
    return firstFetch;
  }


  private void updateSources() throws VcsException {
    BranchInfo branchInfo = null;
    GitFacade git = myGitFactory.create(myTargetDirectory);
    if (isTag(GitUtils.expandRef(mySettings.getRef()))) {
      branchInfo = new BranchInfo(true, false);//this branchInfo will enforce clean
      git.checkout().setForce(true).setBranch(mySettings.getRef()).call();
    } else {
      branchInfo = git.branch().setBranch(mySettings.getRef()).call();
      if (branchInfo.isCurrent) {
        myLogger.message("Resetting " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
        removeIndexLock();
        git.reset().setHard(true).setRevision(myRevision).call();
      } else {
        if (!branchInfo.isExists) {
          git.createBranch()
            .setName(mySettings.getRef())
            .setStartPoint(GitUtils.createRemoteRef(mySettings.getRef()))
            .setTrack(true)
            .call();
        }
        removeRefLock();
        git.updateRef().setRef(GitUtils.expandRef(mySettings.getRef())).setRevision(myRevision).call();
        myLogger.message("Checking out branch " + mySettings.getRef() + " in " + myRoot.getName() + " in " + myTargetDirectory + " with revision " + myRevision);
        git.checkout().setForce(true).setBranch(mySettings.getRef()).call();
      }
    }
    doClean(branchInfo);
    if (mySettings.isCheckoutSubmodules()) {
      checkoutSubmodules(myTargetDirectory);
    }
  }


  private void checkoutSubmodules(@NotNull final File repositoryDir) throws VcsException {
    File gitmodules = new File(repositoryDir, ".gitmodules");
    if (gitmodules.exists()) {
      myLogger.message("Checkout submodules in " + repositoryDir);
      GitFacade git = myGitFactory.create(repositoryDir);
      git.submoduleInit().call();
      git.submoduleUpdate()
        .setAuthSettings(mySettings.getAuthSettings())
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
    return SubmodulesCheckoutPolicy.CHECKOUT.equals(mySettings.getSubmodulesCheckoutPolicy()) ||
           SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS.equals(mySettings.getSubmodulesCheckoutPolicy());
  }



  private void doClean(BranchInfo branchInfo) throws VcsException {
    if (mySettings.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && mySettings.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      myLogger.message("Cleaning " + myRoot.getName() + " in " + myTargetDirectory + " the file set " + mySettings.getCleanFilesPolicy());
      myGitFactory.create(myTargetDirectory).clean().setCleanPolicy(mySettings.getCleanFilesPolicy()).call();
    }
  }


  private boolean isTag(@NotNull String fullRef) {
    return fullRef.startsWith("refs/tags/");
  }


  private void setUseLocalMirror() throws VcsException {
    String remoteUrl = mySettings.getRepositoryFetchURL().toString();
    String localMirrorUrl = getLocalMirrorUrl();
    GitFacade git = myGitFactory.create(myTargetDirectory);
    git.setConfig()
      .setPropertyName("url." + localMirrorUrl + ".insteadOf")
      .setValue(remoteUrl)
      .call();
    git.setConfig()
      .setPropertyName("url." + remoteUrl + ".pushInsteadOf")
      .setValue(remoteUrl)
      .call();
  }

  private void setNotUseLocalMirror() throws VcsException {
    try {
      Repository r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
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
    }
  }

  private String getLocalMirrorUrl() throws VcsException {
    try {
      return new URIish(mySettings.getRepositoryDir().toURI().toASCIIString()).toString();
    } catch (URISyntaxException e) {
      throw new VcsException("Cannot create uri for local mirror " + mySettings.getRepositoryDir().getAbsolutePath(), e);
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


  private boolean isRepositoryUseLocalMirror() throws VcsException {
    try {
      Repository r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      return !config.getSubsections("url").isEmpty();
    } catch (IOException e) {
      String msg = "Error while reading config file in repository " + myTargetDirectory.getAbsolutePath();
      LOG.error(msg, e);
      throw new VcsException(msg, e);
    }
  }


  @Nullable
  private Ref getRef(@NotNull File repositoryDir, @NotNull String ref) {
    List<Ref> refs = myGitFactory.create(repositoryDir).showRef().setPattern(ref).call();
    return refs.isEmpty() ? null : refs.get(0);
  }


  private void updateLocalMirror() throws VcsException {
    File bareRepositoryDir = mySettings.getRepositoryDir();
    String mirrorDescription = "local mirror of root " + myRoot.getName() + " at " + bareRepositoryDir;
    LOG.info("Update " + mirrorDescription);
    boolean fetchRequired = true;
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(bareRepositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(mySettings.getRepositoryFetchURL().toString()).call();
    } else {
      boolean outdatedTagsFound = removeOutdatedTags(bareRepositoryDir);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision " + myRevision + " in " + mirrorDescription);
        Ref ref = getRef(bareRepositoryDir, GitUtils.expandRef(mySettings.getRef()));
        if (ref != null && myRevision.equals(ref.getObjectId().name())) {
          LOG.info("No fetch required for revision '" + myRevision + "' in " + mirrorDescription);
          fetchRequired = false;
        }
      }
    }
    if (fetchRequired)
      fetch(mySettings.getRepositoryDir(), "+" + GitUtils.expandRef(mySettings.getRef()) + ":" + GitUtils.expandRef(mySettings.getRef()), false);
  }


  /**
   * If some git process crashed in this repository earlier it can leave lock files for ref.
   * This method delete such lock file if it exists (with warning message), otherwise git operation will fail.
   */
  private void removeRefLock() {
    String branchRef = GitUtils.expandRef(mySettings.getRef());
    File refLock = new File(myTargetDirectory, ".git" + File.separator + branchRef + ".lock");
    if (refLock.exists()) {
      myLogger.warning("The .git/" + branchRef +
                      ".lock file exists. This probably means a git process crashed in this repository earlier. Deleting lock file");
      FileUtil.delete(refLock);
    }
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


  /**
   * Do fetch operation if needed
   *
   * @param firstFetch true if the directory was just initialized
   * @return the revision information string
   * @throws VcsException if there is a problem with fetching revision
   */
  private String doFetch(boolean firstFetch) throws VcsException {
    String revInfo = null;
    Ref ref = null;
    boolean outdatedTagsFound = false;
    if (!firstFetch) {
      outdatedTagsFound = removeOutdatedTags(myTargetDirectory);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision " + myRevision);
        revInfo = getRevision(myTargetDirectory, myRevision);
        ref = getRef(myTargetDirectory, GitUtils.expandRef(mySettings.getRef()));
      }
    }
    if (!outdatedTagsFound && revInfo != null && ref != null) {//commit and branch exist
      LOG.info("No fetch needed for revision '" + myRevision + "' in " + mySettings.getLocalRepositoryDir());
    } else {
      logStartFetching();
      String previousHead = getPreviousHead(firstFetch);
      if (myPluginConfig.isUseLocalMirrors() && myPluginConfig.isUseShallowClone()) {
        File mirrorRepositoryDir = mySettings.getRepositoryDir();
        String tmpBranchName = createTmpBranch(mirrorRepositoryDir, myRevision);
        String tmpBranchRef = "refs/heads/" + tmpBranchName;
        String refspec = "+" + tmpBranchRef + ":" + GitUtils.createRemoteRef(mySettings.getRef());
        fetch(myTargetDirectory, refspec, true);
        myGitFactory.create(mirrorRepositoryDir).deleteBranch().setName(tmpBranchName).call();
      } else {
        fetch(myTargetDirectory, "+" + GitUtils.expandRef(mySettings.getRef()) + ":" + GitUtils.createRemoteRef(mySettings.getRef()), false);
      }
      String newHead = getRevision(myTargetDirectory, GitUtils.createRemoteRef(mySettings.getRef()));
      if (newHead == null) {
        throw new VcsException("Failed to fetch data for " + mySettings.debugInfo());
      }
      myLogger.message("Fetched revisions " + (previousHead == null ? "up to " : previousHead + "..") + newHead);
      revInfo = getRevision(myTargetDirectory, myRevision);
      if (revInfo == null) {
        throw new VcsException("The revision " + myRevision + " is not found in the repository after fetch " + mySettings.debugInfo());
      }
    }
    return revInfo;
  }

  private String getPreviousHead(boolean firstFetch) {
    if (firstFetch)
      return null;
    return getRevision(myTargetDirectory, GitUtils.createRemoteRef(mySettings.getRef()));
  }

  private void logStartFetching() {
    LOG.info("Fetching in repository " + mySettings.debugInfo());
    myLogger.message("Fetching data for '" + myRoot.getName() + "'...");
  }

  private String createTmpBranch(@NotNull File repositoryDir, @NotNull String branchStartingPoint) throws VcsException {
    String tmpBranchName = getUnusedBranchName(repositoryDir);
    myGitFactory.create(repositoryDir)
      .createBranch()
      .setName(tmpBranchName)
      .setStartPoint(branchStartingPoint)
      .call();
    return tmpBranchName;
  }

  private String getUnusedBranchName(@NotNull File repositoryDir) {
    final String tmpBranchName = "tmp_branch_for_build";
    String branchName = tmpBranchName;
    ShowRefCommand showRef = myGitFactory.create(repositoryDir).showRef();
    int i = 1;
    while (!showRef.setPattern(branchName).call().isEmpty()) {
      branchName = tmpBranchName + i;
      i++;
    }
    return branchName;
  }

  private String getRevision(@NotNull File repositoryDir, @NotNull String revision) {
    return myGitFactory.create(repositoryDir).log()
      .setCommitsNumber(1)
      .setPrettyFormat("%H%x20%s")
      .setStartPoint(revision)
      .call();
  }

  private void fetch(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone) throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);

    FetchCommand fetch = myGitFactory.create(repositoryDir).fetch()
      .setAuthSettings(mySettings.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .setTimeout(timeout)
      .setRefspec(refspec);

    if (silent)
      fetch.setQuite(true);
    else
      fetch.setShowProgress(true);

    if (shallowClone)
      fetch.setDepth(1);

    fetch.call();
  }

  private boolean isSilentFetch() {
    GitVersion version = myPluginConfig.getGitVersion();
    return GIT_WITH_PROGRESS_VERSION.isGreaterThan(version);
  }

  private int getTimeout(boolean silentFetch) {
    if (silentFetch)
      return SILENT_TIMEOUT;
    else
      return myPluginConfig.getIdleTimeoutSeconds();
  }


  private void checkAuthMethodIsSupported() throws VcsException {
    Settings.AuthSettings authSettings = mySettings.getAuthSettings();
    AuthenticationMethod authMethod = authSettings.getAuthMethod();
    switch (authMethod) {
      case PASSWORD:
        GitVersion actualVersion = myPluginConfig.getGitVersion();
        GitVersion requiredVersion = new GitVersion(1, 7, 2);
        if (actualVersion.isLessThan(requiredVersion))
          throw new VcsException("Password authentication requires git " + requiredVersion.toString() + "+, found git version is " + actualVersion + ". " +
                                 "Please install newer git or use '" + AuthenticationMethod.ANONYMOUS.uiName() +
                                 "' or '" + AuthenticationMethod.PRIVATE_KEY_DEFAULT.uiName() + "' authentication methods.");
        break;
      case PRIVATE_KEY_FILE:
        throw new VcsException("TeamCity doesn't support authentication method " + authMethod.uiName() + " with agent checkout. " +
                               "Please use '" + AuthenticationMethod.ANONYMOUS.uiName() + "', '" + AuthenticationMethod.PASSWORD.uiName() +
                               "' or '" + AuthenticationMethod.PRIVATE_KEY_DEFAULT.uiName() + "' authentication methods.");
    }
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
    myGitFactory.create(mySettings.getLocalRepositoryDir())
      .addRemote()
      .setName("origin")
      .setUrl(mySettings.getRepositoryFetchURL().toString())
      .call();
    if (myPluginConfig.isUseLocalMirrors()) setUseLocalMirror();
    URIish url = mySettings.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(mySettings.getRepositoryFetchURL().toString())) {
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("remote.origin.pushurl").setValue(pushUrl).call();
    }
  }


  private void validateUrls() {
    URIish fetch = mySettings.getRepositoryFetchURL();
    if (isAnonymousGitWithUsername(fetch))
      LOG.warn("Fetch URL '" + fetch.toString() + "' for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, fetch will probably fail");
    URIish push  = mySettings.getRepositoryPushURL();
    if (!fetch.equals(push) && isAnonymousGitWithUsername(push))
      LOG.warn("Push URL '" + push.toString() + "'for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, push will probably fail");
  }


  /**
   * Remove outdated tags
   * @param workingDir repository dir
   * @return true if any tags were removed
   */
  private boolean removeOutdatedTags(@NotNull File workingDir) {
    boolean outdatedTagsRemoved = false;
    Tags localTags = getLocalTags(workingDir);
    Tags remoteTags = getRemoteTags(workingDir);
    for (Ref localTag : localTags.list()) {
      if (remoteTags.isOutdated(localTag)) {
        deleteTag(workingDir, localTag.getName());
        outdatedTagsRemoved = true;
      }
    }
    return outdatedTagsRemoved;
  }


  private void deleteTag(@NotNull File workingDir, @NotNull String tagFullName) {
    GitFacade git = myGitFactory.create(workingDir);
    try {
      git.deleteTag().setName(tagFullName).call();
    } catch (VcsException e) {
      LOG.warn("Cannot delete tag " + tagFullName, e);
    }
  }

  private Tags getLocalTags(@NotNull File workingDir) {
    GitFacade git = myGitFactory.create(workingDir);
    return new Tags(git.showRef().showTags().call());
  }

  private Tags getRemoteTags(@NotNull File workingDir) {
    GitFacade git = myGitFactory.create(workingDir);
    return new Tags(git.lsRemote().showTags().call());
  }
}
