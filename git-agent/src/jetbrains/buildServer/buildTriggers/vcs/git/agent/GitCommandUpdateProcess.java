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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * The agent support for VCS.
 */
public class GitCommandUpdateProcess {
  /**
   * the default windows git executable paths
   */
  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS =
    {"C:\\Program Files\\Git\\bin", "C:\\Program Files (x86)\\Git\\bin", "C:\\cygwin\\bin"};
  /**
   * Windows executable name
   */
  @NonNls private static final String DEFAULT_WINDOWS_GIT = "git.exe";
  /**
   * Default UNIX paths
   */
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = {"/usr/local/bin", "/usr/bin", "/opt/local/bin", "/opt/bin"};
  /**
   * UNIX executable name
   */
  @NonNls private static final String DEFAULT_UNIX_GIT = "git";

  /**
   * The logger class
   */
  private final static Logger LOG = Logger.getLogger(GitCommandUpdateProcess.class);
  /**
   * The property that points to git path
   */
  static final String GIT_PATH_PROPERTY = "system.git.executable.path";
  /**
   * The configuration for the agent
   */
  final BuildAgentConfiguration myAgentConfiguration;
  /**
   * The directory cleaner instance
   */
  final SmartDirectoryCleaner myDirectoryCleaner;
  /**
   * The ssh service to use
   */
  final GitAgentSSHService mySshService;
  /**
   * Vcs root
   */
  private VcsRoot myRoot;
  /**
   * Checkout rules
   */
  private CheckoutRules myCheckoutRules;
  /**
   * The version to update to
   */
  private String myToVersion;
  /**
   * The directory where sources should be checked out
   */
  private File myCheckoutDirectory;
  /**
   * The logger for update process
   */
  private BuildProgressLogger mLogger;
  /**
   * The vcs settings
   */
  private AgentSettings mySettings;
  /**
   * The actual directory
   */
  private File myDirectory;
  /**
   * The git revision
   */
  String revision;

  /**
   * The constructor
   *
   * @param agentConfiguration the configuration for this agent
   * @param directoryCleaner   the directory cleaner
   * @param sshService         the used ssh service
   * @param root               the vcs root
   * @param checkoutRules      the checkout rules
   * @param toVersion          the version to update to
   * @param checkoutDirectory  the checkout directory
   * @param logger             the logger
   * @throws VcsException if there is problem with starting the process
   */
  public GitCommandUpdateProcess(@NotNull BuildAgentConfiguration agentConfiguration,
                                 @NotNull SmartDirectoryCleaner directoryCleaner,
                                 @NotNull GitAgentSSHService sshService,
                                 @NotNull VcsRoot root,
                                 @NotNull CheckoutRules checkoutRules,
                                 @NotNull String toVersion,
                                 @NotNull File checkoutDirectory,
                                 @NotNull BuildProgressLogger logger) throws VcsException {
    myAgentConfiguration = agentConfiguration;
    myDirectoryCleaner = directoryCleaner;
    mySshService = sshService;
    myRoot = root;
    myCheckoutRules = checkoutRules;
    myToVersion = toVersion;
    myCheckoutDirectory = checkoutDirectory;
    mLogger = logger;
    revision = GitUtils.versionRevision(toVersion);
    myDirectory = findDirectory();
    mySettings = new AgentSettings(getGitPath(), myDirectory, root);
  }

  public void canRun() throws VcsException {
    String path = getGitPath();
    if (path == null) {
      throw new VcsException("The path to git executable is not configured (the property name is system.git.excecutable.path)");
    }
    GitVersion v;
    try {
      v = new VersionCommand(mySettings.getCommandSettings()).version();
    } catch (VcsException e) {
      throw new VcsException("Unable to run git at path " + path, e);
    }
    if (!GitVersion.MIN.isLessOrEqual(v)) {
      throw new VcsException("Unsupported version of Git is detected at (" + path + "): " + v);
    }
  }

  /**
   * @return the path to the git executable or null if neither configured nor found
   */
  private String getGitPath() {
    String path = myAgentConfiguration.getCustomProperties().get(GIT_PATH_PROPERTY);
    return path == null ? defaultGit() : path;
  }

  /**
   * Update sources
   *
   * @throws VcsException the exception to use
   */
  public void updateSources() throws VcsException {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myCheckoutDirectory + " to revision " + myToVersion);
    canRun();
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
        dirUrl = new ConfigCommand(mySettings).get("remote.origin.url");
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
    BranchCommand.BranchInfo branchInfo = new BranchCommand(mySettings).branchInfo(mySettings.getBranch());
    if (branchInfo.isCurrent) {
      // Force tracking of origin/branch
      forceTrackingBranch();
      // Hard reset to the required revision.
      mLogger.message("Resetting " + myRoot.getName() + " in " + myDirectory + " to revision " + revInfo);
      new ResetCommand(mySettings).hardReset(revision);
    } else {
      // create branch if missing to track remote
      if (!branchInfo.isExists) {
        new BranchCommand(mySettings).createBranch(mySettings.getBranch(), GitUtils.remotesBranchRef(mySettings.getBranch()));
      } else {
        // Force tracking of origin/branch
        forceTrackingBranch();
      }
      // update-ref to specified revision
      new BranchCommand(mySettings).setBranchCommit(mySettings.getBranch(), revision);
      // checkout branch
      mLogger.message(
        "Checking out branch " + mySettings.getBranch() + " in " + myRoot.getName() + " in " + myDirectory + " with revision " + revInfo);
      new BranchCommand(mySettings).forceCheckout(mySettings.getBranch());
    }
    // do clean if requested
    if (mySettings.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && mySettings.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      mLogger.message("Cleaning " + myRoot.getName() + " in " + myDirectory + " the file set " + mySettings.getCleanFilesPolicy());
      new CleanCommand(mySettings).clean();
    }
    if (new File(myDirectory, ".gitmodules").exists() && mySettings.areSubmodulesCheckedOut()) {
      throw new VcsException("Submodule checkout is not supported on agent " + myRoot.getName());
    }
  }

  /**
   * Force tracking branch to origin's branch
   *
   * @throws VcsException if there problem with running git
   */
  private void forceTrackingBranch() throws VcsException {
    new ConfigCommand(mySettings).set("branch." + mySettings.getBranch() + ".remote", "origin");
    new ConfigCommand(mySettings).set("branch." + mySettings.getBranch() + ".merge", GitUtils.branchRef(mySettings.getBranch()));
  }

  /**
   * Do fetch operation if needed
   *
   * @param firstFetch true if the directory was just initialized
   * @return the revision information string
   * @throws VcsException if there is a problem with fetching revision
   */
  private String doFetch(boolean firstFetch) throws VcsException {
    String revInfo = firstFetch ? null : new LogCommand(mySettings).checkRevision(revision);
    if (revInfo != null) {
      LOG.info("No fetch needed for revision '" + revision + "' in " + mySettings.getCommandSettings().getLocalRepositoryDir());
    } else {
      if (!"git".equals(mySettings.getRepositoryFetchURL().getScheme()) &&
          (mySettings.getAuthenticationMethod() == AuthenticationMethod.PASSWORD ||
           mySettings.getAuthenticationMethod() == AuthenticationMethod.PRIVATE_KEY_FILE)) {
        throw new VcsException("The authentication method is not supported for agent checkout: " + mySettings.getAuthenticationMethod());
      }
      LOG.info("Fetching in repository " + mySettings.debugInfo());
      mLogger.message("Fetching data for '" + myRoot.getName() + "'...");
      String previousHead = new LogCommand(mySettings).checkRevision(GitUtils.remotesBranchRef(mySettings.getBranch()));
      firstFetch |= previousHead == null;
      new FetchCommand(mySettings, mySshService).fetch();
      String newHead = new LogCommand(mySettings).checkRevision(GitUtils.remotesBranchRef(mySettings.getBranch()));
      if (newHead == null) {
        throw new VcsException("Failed to fetch data for " + mySettings.debugInfo());
      }
      mLogger.message("Fetched revisions " + (previousHead == null ? "up to " : previousHead + "..") + newHead);
      revInfo = new LogCommand(mySettings).checkRevision(revision);
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
    new InitCommand(mySettings.getCommandSettings()).init();
    new RemoteCommand(mySettings).add("origin", mySettings.getRepositoryFetchURL().toString());
    URIish url = mySettings.getRepositoryPushURL();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(mySettings.getRepositoryFetchURL().toString())) {
      new ConfigCommand(mySettings).set("remote.origin.pushurl", pushUrl);
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
        throw new VcsException("The include rule must have a form '. => subdir' (" + ir.toDescriptiveString() +
                               ") for VCS Root " + myRoot.getName());
      }
    }
  }

  /**
   * @return the default executable name depending on the platform
   */
  private static String defaultGit() {
    String[] paths;
    String program;
    if (SystemInfo.isWindows) {
      program = DEFAULT_WINDOWS_GIT;
      paths = DEFAULT_WINDOWS_PATHS;
    } else {
      program = DEFAULT_UNIX_GIT;
      paths = DEFAULT_UNIX_PATHS;
    }
    for (String p : paths) {
      File f = new File(p, program);
      if (f.exists()) {
        return f.getAbsolutePath();
      }
    }
    return null;
  }
}
