/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefCommand;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isAnonymousGitWithUsername;

public class UpdaterImpl implements Updater {

  private final static Logger LOG = Logger.getLogger(UpdaterImpl.class);

  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final BuildProgressLogger myLogger;
  private final AgentPluginConfig myPluginConfig;
  private final GitFacade myGit;
  private final VcsRoot myRoot;
  private final File myTargetDirectory;
  private final String myRevision;
  private final AgentSettings mySettings;

  public UpdaterImpl(@NotNull AgentPluginConfig pluginConfig,
                     @NotNull MirrorManager mirrorManager,
                     @NotNull SmartDirectoryCleaner directoryCleaner,
                     @NotNull GitFacade git,
                     @NotNull BuildProgressLogger logger,
                     @NotNull VcsRoot root,
                     @NotNull String version,
                     @NotNull File targetDir) throws VcsException {
    myPluginConfig = pluginConfig;
    myDirectoryCleaner = directoryCleaner;
    myGit = git;
    myLogger = logger;
    myRoot = root;
    myRevision = GitUtils.versionRevision(version);
    myTargetDirectory = targetDir;
    mySettings = new AgentSettings(myPluginConfig, mirrorManager, myTargetDirectory, root);
  }


  public void update() throws VcsException {
    if (myPluginConfig.isUseLocalMirrors())
      updateLocalMirror();

    LOG.info("Starting update of root " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
    LOG.debug("Updating " + mySettings.debugInfo());

    boolean firstFetch = initGitRepository();
    String revInfo = doFetch(firstFetch);
    updateSources(revInfo);
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


  private void updateSources(String revInfo) throws VcsException {
    BranchInfo branchInfo = null;
    if (isTag(GitUtils.expandRef(mySettings.getRef()))) {
      branchInfo = new BranchInfo(true, false);//this branchInfo will enforce clean
      myGit.forceCheckout(mySettings, mySettings.getRef());
    } else {
      branchInfo = myGit.getBranchInfo(mySettings, mySettings.getRef());
      if (branchInfo.isCurrent) {
        myLogger.message("Resetting " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + revInfo);
        removeIndexLock();
        myGit.hardReset(mySettings, myRevision);
      } else {
        if (!branchInfo.isExists) {
          myGit.createBranch(mySettings, mySettings.getRef());
        }
        removeRefLock();
        myGit.setBranchCommit(mySettings, mySettings.getRef(), myRevision);
        myLogger.message("Checking out branch " + mySettings.getRef() + " in " + myRoot.getName() + " in " + myTargetDirectory + " with revision " + revInfo);
        myGit.forceCheckout(mySettings, mySettings.getRef());
      }
    }
    doClean(branchInfo);
    if (mySettings.isCheckoutSubmodules()) {
      myGit.doSubmoduleUpdate(mySettings, myTargetDirectory);
    }
  }


  private void doClean(BranchInfo branchInfo) throws VcsException {
    if (mySettings.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && mySettings.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      myLogger.message("Cleaning " + myRoot.getName() + " in " + myTargetDirectory + " the file set " + mySettings.getCleanFilesPolicy());
      myGit.clean(mySettings, branchInfo);
    }
  }


  private boolean isTag(@NotNull String fullRef) {
    return fullRef.startsWith("refs/tags/");
  }


  private void setUseLocalMirror() throws VcsException {
    String localMirrorUrl = getLocalMirrorUrl();
    myGit.setConfigProperty(mySettings, "url." + localMirrorUrl + ".insteadOf", mySettings.getRepositoryFetchURL().toString());
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
      return myGit.getConfigProperty(mySettings, "remote.origin.url");
    } catch (VcsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to read property", e);
      }
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


  private Ref getRef(String ref) {
    ShowRefCommand command = new ShowRefCommand(mySettings);
    command.setRef(ref);
    List<Ref> refs = command.execute();
    if (refs.isEmpty())
      return null;
    else
      return refs.get(0);
  }


  private void updateLocalMirror() throws VcsException {
    File bareRepositoryDir = mySettings.getRepositoryDir();
    String mirrorDescription = "local mirror of root " + myRoot.getName() + " at " + bareRepositoryDir;
    LOG.info("Update " + mirrorDescription);
    boolean fetchRequired = true;
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      myGit.initBare(mySettings);
      myGit.addRemoteBare(mySettings, "origin", mySettings.getRepositoryFetchURL());
    } else {
      LOG.debug("Try to find revision " + myRevision + " in " + mirrorDescription);
      Ref ref = getRef(GitUtils.expandRef(mySettings.getRef()));
      if (ref != null && myRevision.equals(ref.getObjectId().name())) {
        LOG.info("No fetch required for revision '" + myRevision + "' in " + mirrorDescription);
        fetchRequired = false;
      } else {
        fetchRequired = true;
      }
    }
    if (fetchRequired)
      myGit.fetchBare(mySettings);
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
    if (!firstFetch) {
      LOG.debug("Try to find revision " + myRevision);
      revInfo = myGit.checkRevision(mySettings, myRevision, "debug");
      ref = getRef(GitUtils.expandRef(mySettings.getRef()));
    }
    if (revInfo != null && ref != null) {//commit and branch exist
      LOG.info("No fetch needed for revision '" + myRevision + "' in " + mySettings.getLocalRepositoryDir());
    } else {
      checkAuthMethodIsSupported();
      LOG.info("Fetching in repository " + mySettings.debugInfo());
      myLogger.message("Fetching data for '" + myRoot.getName() + "'...");
      String previousHead = null;
      if (!firstFetch) {
        previousHead = myGit.checkRevision(mySettings, GitUtils.createRemoteRef(mySettings.getRef()));
      }
      myGit.fetch(mySettings);
      String newHead = myGit.checkRevision(mySettings, GitUtils.createRemoteRef(mySettings.getRef()));
      if (newHead == null) {
        throw new VcsException("Failed to fetch data for " + mySettings.debugInfo());
      }
      myLogger.message("Fetched revisions " + (previousHead == null ? "up to " : previousHead + "..") + newHead);
      revInfo = myGit.checkRevision(mySettings, myRevision);
      if (revInfo == null) {
        throw new VcsException("The revision " + myRevision + " is not found in the repository after fetch " + mySettings.debugInfo());
      }
    }
    return revInfo;
  }

  private void checkAuthMethodIsSupported() throws VcsException {
    if (!"git".equals(mySettings.getRepositoryFetchURL().getScheme()) &&
        (mySettings.getAuthSettings().getAuthMethod() == AuthenticationMethod.PASSWORD ||
         mySettings.getAuthSettings().getAuthMethod() == AuthenticationMethod.PRIVATE_KEY_FILE)) {
      throw new VcsException("TeamCity doesn't support authentication method " + mySettings.getAuthSettings().getAuthMethod().uiName() + " with agent checkout. " +
      "Please use '" + AuthenticationMethod.ANONYMOUS.uiName() + "' or '" + AuthenticationMethod.PRIVATE_KEY_DEFAULT.uiName() + "' methods.");
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
    myGit.init(mySettings);
    validateUrls();
    myGit.addRemote(mySettings, "origin", mySettings.getRepositoryFetchURL());
    if (myPluginConfig.isUseLocalMirrors()) setUseLocalMirror();
    URIish url = mySettings.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(mySettings.getRepositoryFetchURL().toString())) {
      myGit.setConfigProperty(mySettings, "remote.origin.pushurl", pushUrl);
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
}
