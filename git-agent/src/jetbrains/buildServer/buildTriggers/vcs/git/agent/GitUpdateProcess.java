/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * The agent support for VCS.
 */
public abstract class GitUpdateProcess {

  /**
   * The logger class
   */
  private final static Logger LOG = Logger.getLogger(GitUpdateProcess.class);
  /**
   * The configuration for the agent
   */
  final BuildAgentConfiguration myAgentConfiguration;
  /**
   * The directory cleaner instance
   */
  final SmartDirectoryCleaner myDirectoryCleaner;
  /**
   * Vcs root
   */
  protected final VcsRoot myRoot;
  /**
   * Checkout rules
   */
  protected final CheckoutRules myCheckoutRules;
  /**
   * The version to update to
   */
  protected final String myToVersion;
  /**
   * The directory where sources should be checked out
   */
  protected final File myCheckoutDirectory;
  /**
   * The logger for update process
   */
  protected final BuildProgressLogger mLogger;
  /**
   * The vcs settings
   */
  protected final AgentSettings mySettings;
  /**
   * The actual directory
   */
  protected final File myDirectory;
  /**
   * The git revision
   */
  protected final String revision;

  /**
   * The constructor
   *
   * @param agentConfiguration the configuration for this agent
   * @param directoryCleaner   the directory cleaner
   * @param root               the vcs root
   * @param checkoutRules      the checkout rules
   * @param toVersion          the version to update to
   * @param checkoutDirectory  the checkout directory
   * @param logger             the logger
   * @param gitPath            the path to git
   * @throws VcsException if there is problem with starting the process
   */
  public GitUpdateProcess(@NotNull BuildAgentConfiguration agentConfiguration,
                          @NotNull SmartDirectoryCleaner directoryCleaner,
                          @NotNull VcsRoot root,
                          @NotNull CheckoutRules checkoutRules,
                          @NotNull String toVersion,
                          @NotNull File checkoutDirectory,
                          @NotNull BuildProgressLogger logger,
                          @Nullable String gitPath,
                          boolean useNativeSSH) throws VcsException {
    myAgentConfiguration = agentConfiguration;
    myDirectoryCleaner = directoryCleaner;
    myRoot = root;
    myCheckoutRules = checkoutRules;
    myToVersion = toVersion;
    myCheckoutDirectory = checkoutDirectory;
    mLogger = logger;
    revision = GitUtils.versionRevision(toVersion);
    myDirectory = findDirectory();
    mySettings = new AgentSettings(gitPath, myDirectory, root, useNativeSSH);
  }

  /**
   * Update sources
   *
   * @throws VcsException the exception to use
   */
  public void updateSources() throws VcsException {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myCheckoutDirectory + " to revision " + myToVersion);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Updating " + mySettings.debugInfo());
    }
    String url = mySettings.getRepositoryFetchURL().toString();
    // clean directory if origin does not matches fetch URL or it is non-git directory
    boolean firstFetch = false;
    if (!new File(myDirectory, ".git").exists()) {
      initDirectory();
      firstFetch = true;
    } else {
      String dirUrl;
      try {
        dirUrl = getConfigProperty("remote.origin.url");
      } catch (VcsException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Failed to read property", e);
        }
        dirUrl = "";
      }
      if (!dirUrl.equals(url)) {
        initDirectory();
        firstFetch = true;
      }
    }
    // fetch data from the repository
    String revInfo = doFetch(firstFetch);
    // check what is the current branch
    BranchInfo branchInfo = getBranchInfo(mySettings.getBranch());
    if (branchInfo.isCurrent) {
      // Force tracking of origin/branch
      forceTrackingBranch();
      // Hard reset to the required revision.
      mLogger.message("Resetting " + myRoot.getName() + " in " + myDirectory + " to revision " + revInfo);
      removeIndexLock();
      hardReset();
    } else {
      // create branch if missing to track remote
      if (!branchInfo.isExists) {
        createBranch();
      } else {
        // Force tracking of origin/branch
        forceTrackingBranch();
      }
      removeRefLock();
      // update-ref to specified revision
      setBranchCommit();
      // checkout branch
      mLogger.message(
        "Checking out branch " + mySettings.getBranch() + " in " + myRoot.getName() + " in " + myDirectory + " with revision " + revInfo);
      forceCheckout();
    }
    // do clean if requested
    doClean(branchInfo);
    if (mySettings.isCheckoutSubmodules()) {
      doSubmoduleUpdate(myDirectory);
    }
  }

  /**
   * If some git process crashed in this repository earlier it can leave lock files for ref.
   * This method delete such lock file if it exists (with warning message), otherwise git operation will fail.
   */
  private void removeRefLock() {
    String branchRef = GitUtils.branchRef(mySettings.getBranch());
    File refLock = new File(myDirectory, ".git" + File.separator + branchRef + ".lock");
    if (refLock.exists()) {
      mLogger.warning("The .git/" + branchRef + ".lock file exists. This probably means a git process crashed in this repository earlier. Deleting lock file");
      FileUtil.delete(refLock);
    }
  }

  /**
   * If some git process crashed in this repository earlier it can leave lock files for index.
   * This method delete such lock file if it exists (with warning message), otherwise git operation will fail.
   */
  private void removeIndexLock() {
    File indexLock = new File(myDirectory, ".git" + File.separator + "index.lock");
    if (indexLock.exists()) {
      mLogger.warning("The .git/index.lock file exists. This probably means a git process crashed in this repository earlier. Deleting lock file");
      FileUtil.delete(indexLock);
    }
  }


  /**
   * Force tracking branch to origin's branch
   *
   * @throws VcsException if there problem with running git
   */
  private void forceTrackingBranch() throws VcsException {
    setConfigProperty("branch." + mySettings.getBranch() + ".remote", "origin");
    setConfigProperty("branch." + mySettings.getBranch() + ".merge", GitUtils.branchRef(mySettings.getBranch()));
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
    if (!firstFetch) {
      LOG.debug("Try to find revision " + revision);
      revInfo = checkRevision(revision, "debug");
    }
    if (revInfo != null) {
      LOG.info("No fetch needed for revision '" + revision + "' in " + mySettings.getLocalRepositoryDir());
    } else {
      if (!"git".equals(mySettings.getRepositoryFetchURL().getScheme()) &&
          (mySettings.getAuthSettings().getAuthMethod() == AuthenticationMethod.PASSWORD ||
           mySettings.getAuthSettings().getAuthMethod() == AuthenticationMethod.PRIVATE_KEY_FILE)) {
        throw new VcsException("TeamCity doesn't support authentication method " + mySettings.getAuthSettings().getAuthMethod().uiName() + " with agent checkout. " +
        "Please use '" + AuthenticationMethod.ANONYMOUS.uiName() + "' or '" + AuthenticationMethod.PRIVATE_KEY_DEFAULT.uiName() + "' methods.");
      }
      LOG.info("Fetching in repository " + mySettings.debugInfo());
      mLogger.message("Fetching data for '" + myRoot.getName() + "'...");
      String previousHead = null;
      if (!firstFetch) {
        previousHead = checkRevision(GitUtils.remotesBranchRef(mySettings.getBranch()));
      }
      fetch();
      String newHead = checkRevision(GitUtils.remotesBranchRef(mySettings.getBranch()));
      if (newHead == null) {
        throw new VcsException("Failed to fetch data for " + mySettings.debugInfo());
      }
      mLogger.message("Fetched revisions " + (previousHead == null ? "up to " : previousHead + "..") + newHead);
      revInfo = checkRevision(revision);
    }
    if (revInfo == null) {
      throw new VcsException("The revision " + revision + " is not found in the repository after fetch " + mySettings.debugInfo());
    }
    return revInfo;
  }

  /**
   * Clean and init directory and configure remote origin
   *
   * @throws VcsException if there are problems with initializing the directory
   */
  void initDirectory()
    throws VcsException {
    BuildDirectoryCleanerCallback c = new BuildDirectoryCleanerCallback(mLogger, LOG);
    myDirectoryCleaner.cleanFolder(myDirectory, c);
    //noinspection ResultOfMethodCallIgnored
    myDirectory.mkdirs();
    if (c.isHasErrors()) {
      throw new VcsException("Unable to clean directory " + myDirectory + " for VCS root " + myRoot.getName());
    }
    mLogger.message("The .git directory is missing in '" + myDirectory + "'. Running 'git init'...");
    init();
    addRemote("origin", mySettings.getRepositoryFetchURL());
    URIish url = mySettings.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(mySettings.getRepositoryFetchURL().toString())) {
      setConfigProperty("remote.origin.pushurl", pushUrl);
    }
  }

  /**
   * Get the destination directory creating it if it is missing
   *
   * @return the directory where vcs root should be checked out according to checkout rules
   * @throws VcsException if the directory could not be located or created
   */
  private File findDirectory() throws VcsException {
    validateCheckoutRules();
    String path = myCheckoutRules.map("");
    if (path == null) {
      throw new VcsException("The root path could not be mapped for " + myRoot.getName());
    }
    File directory = path.length() == 0 ? myCheckoutDirectory : new File(myCheckoutDirectory, path.replace('/', File.separatorChar));
    if (!directory.exists()) {
      mLogger.message("The destination directory'" + directory + "' is missing. creating it...");
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
      if (!directory.exists()) {
        throw new VcsException("The destination directory '" + directory + "' could not be created.");
      }
    }
    return directory;
  }

  /**
   * Validate checkout rules for update request
   *
   * @throws VcsException if invalid checkout rules are encountered
   */
  private void validateCheckoutRules() throws VcsException {
    if (myCheckoutRules.getExcludeRules().size() != 0) {
      throw new VcsException(
        "The exclude rules are not supported for agent checkout for the git (" + myCheckoutRules.getExcludeRules().size() +
        " rule(s) detected) for VCS Root " + myRoot.getName());
    }
    if (myCheckoutRules.getIncludeRules().size() > 1) {
      throw new VcsException(
        "At most one include rule is supported for agent checkout for the git (" + myCheckoutRules.getIncludeRules().size() +
        " rule(s) detected) for VCS Root " + myRoot.getName());
    }
    if (myCheckoutRules.getIncludeRules().size() == 1) {
      IncludeRule ir = myCheckoutRules.getIncludeRules().get(0);
      if (!".".equals(ir.getFrom()) && ir.getFrom().length() != 0) {
        throw new VcsException("Agent checkout for the git supports only include rule of form '. => subdir', rule " + ir.toDescriptiveString() +
                               " for VCS Root " + myRoot.getName() + " is not supported");
      }
    }
  }

  /**
   * Add remote to the repository
   *
   * @param name     the remote name
   * @param fetchUrl the fetch URL
   * @throws VcsException if repository cannot be accessed
   */
  protected abstract void addRemote(String name, URIish fetchUrl) throws VcsException;

  /**
   * Init repository
   *
   * @throws VcsException if repository cannot be accessed
   */
  protected abstract void init() throws VcsException;


  /**
   * Create branch
   *
   * @param branch the branch name
   * @return information about the branch
   * @throws VcsException if branch information could not be retrieved
   */
  protected abstract BranchInfo getBranchInfo(String branch) throws VcsException;

  /**
   * Get configuration property
   *
   * @param propertyName the property name
   * @return the property value
   * @throws VcsException if there is problem with getting property
   */
  protected abstract String getConfigProperty(String propertyName) throws VcsException;

  /**
   * Set configuration property value
   *
   * @param propertyName the property name
   * @param value        the property value
   * @throws VcsException if the property could not be set
   */
  protected abstract void setConfigProperty(String propertyName, String value) throws VcsException;

  /**
   * Hard reset to the specified revision
   *
   * @throws VcsException if there is a prolem with accessing repository
   */
  protected abstract void hardReset() throws VcsException;

  /**
   * Perform clean according to the settings
   *
   * @param branchInfo the branch information to use
   * @throws VcsException if there is a problem with accessing repository
   */
  protected abstract void doClean(BranchInfo branchInfo) throws VcsException;

  /**
   * Force checkout of the branch removing files that are no more under VCS
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  protected abstract void forceCheckout() throws VcsException;

  /**
   * Set commit on non-active branch
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  protected abstract void setBranchCommit() throws VcsException;

  /**
   * Create branch
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  protected abstract void createBranch() throws VcsException;

  /**
   * Perform fetch operation
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  protected abstract void fetch() throws VcsException;

  /**
   * Check the specified revision
   *
   * @param revision       the revision expression to check
   * @param errorsLogLevel log level to use for reporting errors of native git command
   * @return a short revision information or null if revision is not found
   */
  protected abstract String checkRevision(final String revision, final String... errorsLogLevel);

  /**
   * Make submodule init and submodule update
   *
   * @throws VcsException
   */
  protected abstract void doSubmoduleUpdate(File dir) throws VcsException;

  /**
   * The branch information class
   */
  public static class BranchInfo {
    /**
     * True if the branch exists
     */
    public final boolean isExists;
    /**
     * True if the branch is the current branch
     */
    public final boolean isCurrent;

    /**
     * The constructor
     *
     * @param exists  if true, the branch exists
     * @param current if true the branch is the current branch
     */
    public BranchInfo(boolean exists, boolean current) {
      isExists = exists;
      isCurrent = current;
    }
  }
}
