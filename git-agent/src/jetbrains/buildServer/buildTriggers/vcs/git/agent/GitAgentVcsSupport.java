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
import jetbrains.buildServer.TextLogger;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.vcs.AgentVcsSupport;
import jetbrains.buildServer.agent.vcs.UpdateByCheckoutRules;
import jetbrains.buildServer.agent.vcs.UpdatePolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * The agent support for VCS.
 */
public class GitAgentVcsSupport extends AgentVcsSupport implements UpdateByCheckoutRules {
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
  private final static Logger LOG = Logger.getLogger(GitAgentVcsSupport.class);
  /**
   * The property that points to git path
   */
  static final String GIT_PATH_PROPERTY = "system.git.executable.path";
  /**
   * The configuration for the agent
   */
  final BuildAgentConfiguration agentConfiguration;
  /**
   * The directory cleaner instance
   */
  final SmartDirectoryCleaner directoryCleaner;

  /**
   * The constructor
   *
   * @param agentConfiguration the configuration for this agent
   * @param directoryCleaner   the directory cleaner
   */
  public GitAgentVcsSupport(BuildAgentConfiguration agentConfiguration, SmartDirectoryCleaner directoryCleaner) {
    this.agentConfiguration = agentConfiguration;
    this.directoryCleaner = directoryCleaner;
  }

  @Override
  public boolean canRun(@NotNull BuildAgentConfiguration agentConfiguration, @NotNull TextLogger messageLog) {
    String path = getGitPath();
    if (path == null) {
      String msg = "The path to git executable is not configured (the property name is system.git.excecutable.path)";
      messageLog.error(msg);
      LOG.error(msg);
      return false;
    }
    GitVersion v;
    try {
      v = new VersionCommand(getSetting()).version();
    } catch (VcsException e) {
      messageLog.error("Unable to run git: " + e);
      LOG.error("Unable to run git at path " + path, e);
      return false;
    }
    if (!GitVersion.MIN.isLessOrEqual(v)) {
      String msg = "Unsupported version of Git is detected at (" + path + "): " + v;
      messageLog.error(msg);
      LOG.error(msg);
      return false;
    }
    LOG.info("The Git version " + v + " is detected.");
    return true;
  }

  /**
   * @return the path to the git executable or null if neither configured nor found
   */
  private String getGitPath() {
    String path = agentConfiguration.getCustomProperties().get(GIT_PATH_PROPERTY);
    return path == null ? defaultGit() : path;
  }

  /**
   * @return get settings object that use current directory as a work directory (for commands that directory-independent)
   * @throws VcsException if invalid settings are detected
   */
  private Settings getSetting() throws VcsException {
    return getSetting(new File("."));  //To change body of created methods use File | Settings | File Templates.
  }

  /**
   * Get settings object for the specific directory
   *
   * @param workingDirectory the working directory
   * @return created settings object
   * @throws VcsException if invalid settings are detected
   */
  private Settings getSetting(File workingDirectory) throws VcsException {
    return new Settings(getGitPath(), workingDirectory, null);
  }

  /**
   * Get settings object for the specific directory and root properties
   *
   * @param root      the vcs root to take settings from
   * @param directory the working directory
   * @return created settings object
   * @throws VcsException if invalid settings are detected in vcs root
   */
  private Settings getSettings(VcsRoot root, File directory) throws VcsException {
    return new Settings(getGitPath(), directory, root);
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
                            @NotNull CheckoutRules checkoutRules,
                            @NotNull String toVersion,
                            @NotNull File checkoutDirectory,
                            @NotNull BuildProgressLogger logger) throws VcsException {
    LOG.info("Starting update of root " + root.getName() + " in " + checkoutDirectory + " to revision " + toVersion);
    File directory = getDirectory(root, checkoutRules, checkoutDirectory, logger);
    Settings s = getSettings(root, directory);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Updating " + s.debugInfo());
    }
    String url = s.getFetchUrl();
    // clean directory if origin does not matches fetch URL or it is non-git directory
    boolean firstFetch = false;
    if (!new File(directory, ".git").exists()) {
      initDirectory(root, s, directory, logger);
      firstFetch = true;
    } else {
      String dirUrl;
      try {
        dirUrl = new ConfigCommand(s).get("remote.origin.url");
      } catch (VcsException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Failed to read property", e);
        }
        dirUrl = "";
      }
      if (!dirUrl.equals(url)) {
        initDirectory(root, s, directory, logger);
        firstFetch = true;
      }
    }
    // fetch data from the repository
    String revision = GitUtils.versionRevision(toVersion);
    String revInfo = doFetch(root, logger, s, firstFetch, revision);
    // check what is the current branch
    BranchCommand.BranchInfo branchInfo = new BranchCommand(s).branchInfo(s.getBranch());
    if (branchInfo.isCurrent) {
      // Force tracking of origin/branch
      forceTrackingBranch(s);
      // Hard reset to the required revision.
      logger.message("Resetting " + root.getName() + " in " + directory + " to revision " + revInfo);
      new ResetCommand(s).hardReset(revision);
    } else {
      // create branch if missing to track remote
      if (!branchInfo.isExists) {
        new BranchCommand(s).createBranch(s.getBranch(), GitUtils.remotesBranchRef(s.getBranch()));
      } else {
        // Force tracking of origin/branch
        forceTrackingBranch(s);
      }
      // update-ref to specified revision
      new BranchCommand(s).setBranchCommit(s.getBranch(), revision);
      // checkout branch
      logger.message("Checking out branch " + s.getBranch() + " in " + root.getName() + " in " + directory + " with revision " + revInfo);
      new BranchCommand(s).forceCheckout(s.getBranch());
    }
    // do clean if requested
    if (s.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && s.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      logger.message("Cleaning " + root.getName() + " in " + directory + " the file set " + s.getCleanFilesPolicy());
      new CleanCommand(s).clean();
    }
    if (new File(directory, ".gitmodules").exists() && s.areSubmodulesCheckedOut()) {
      throw new VcsException("Submodule checkout is not supported on agent " + root.getName());
    }
  }

  /**
   * Force tracking branch to origin's branch
   *
   * @param s settings to use
   * @throws VcsException if there problem with running git
   */
  private void forceTrackingBranch(Settings s) throws VcsException {
    new ConfigCommand(s).set("branch." + s.getBranch() + ".remote", "origin");
    new ConfigCommand(s).set("branch." + s.getBranch() + ".merge", GitUtils.branchRef(s.getBranch()));
  }

  /**
   * Do fetch operation if needed
   *
   * @param root       the VCS root
   * @param logger     the build logger
   * @param s          the settings object
   * @param firstFetch true if the directory was just initialized
   * @param revision   the revision to fetch
   * @return a revision information string
   * @throws VcsException if there is a problem with fetching revision
   */
  private String doFetch(VcsRoot root, BuildProgressLogger logger, Settings s, boolean firstFetch, String revision) throws VcsException {
    String revInfo = firstFetch ? null : new LogCommand(s).checkRevision(revision);
    if (revInfo != null) {
      LOG.info("No fetch needed for revision '" + revision + "' in " + s.getLocalRepositoryDir());
    } else {
      if (!s.getFetchUrl().startsWith("git:") &&
          (s.getAuthenticationMethod() == AuthenticationMethod.PASSWORD ||
           s.getAuthenticationMethod() == AuthenticationMethod.PRIVATE_KEY_FILE)) {
        throw new VcsException("The authentication method is not supported for agent checkout: " + s.getAuthenticationMethod());
      }
      LOG.info("Fetching in repository " + s.debugInfo());
      logger.message("Fetching data for '" + root.getName() + "'...");
      String previousHead = new LogCommand(s).checkRevision(GitUtils.remotesBranchRef(s.getBranch()));
      firstFetch |= previousHead == null;
      new FetchCommand(s).fetch(firstFetch);
      String newHead = new LogCommand(s).checkRevision(GitUtils.remotesBranchRef(s.getBranch()));
      if (newHead == null) {
        throw new VcsException("Failed to fetch data for " + s.debugInfo());
      }
      logger.message("Fetched revisions " + (previousHead == null ? "up to " : previousHead + "..") + newHead);
      revInfo = new LogCommand(s).checkRevision(revision);
    }
    if (revInfo == null) {
      throw new VcsException("The revision " + revision + " is not found in the repository after fetch " + s.debugInfo());
    }
    return revInfo;
  }

  /**
   * Clean and init directory and configure remote origin
   *
   * @param root     the VCS root
   * @param settings the VCS settings
   * @param dir      the directory to clean
   * @param logger   the logger
   * @throws VcsException if there are problems with initializing the directory
   */
  void initDirectory(@NotNull VcsRoot root, @NotNull Settings settings, @NotNull File dir, @NotNull BuildProgressLogger logger)
    throws VcsException {
    BuildDirectoryCleanerCallback c = new BuildDirectoryCleanerCallback(logger, LOG);
    directoryCleaner.cleanFolder(dir, c);
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();
    if (c.isHasErrors()) {
      throw new VcsException("Unable to clean directory " + dir + " for VCS root " + root.getName());
    }
    logger.message("The .git directory is missing in '" + dir + "'. Running 'git init'...");
    new InitCommand(settings).init();
    new RemoteCommand(settings).add("origin", settings.getFetchUrl());
    String pushUrl = settings.getPushUrl();
    if (pushUrl != null && !pushUrl.equals(settings.getFetchUrl())) {
      new ConfigCommand(settings).set("remote.origin.pushurl", pushUrl);
    }
  }

  /**
   * Get the destination directory creating it if it is missing
   *
   * @param root              the VCS root
   * @param checkoutRules     the checkout rules for this root
   * @param checkoutDirectory the root checkout directory for the project
   * @param logger            the progress logger
   * @return the directory where vcs root should be checked out according to checkout rules
   * @throws VcsException if the directory could not be located or created
   */
  private File getDirectory(@NotNull VcsRoot root,
                            @NotNull CheckoutRules checkoutRules,
                            @NotNull File checkoutDirectory,
                            @NotNull BuildProgressLogger logger) throws VcsException {
    validateCheckoutRules(root, checkoutRules);
    String path = checkoutRules.map("");
    if (path == null) {
      throw new VcsException("The root path could not be mapped for " + root.getName());
    }
    File directory = path.length() == 0 ? checkoutDirectory : new File(checkoutDirectory, path.replace('/', File.separatorChar));
    if (!directory.exists()) {
      logger.message("The destination directory'" + directory + "' is missing. creating it...");
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
   * @param root          the VCS root
   * @param checkoutRules the checkout rules to validate
   * @throws VcsException if invalid checkout rules are encountered
   */
  private void validateCheckoutRules(VcsRoot root, CheckoutRules checkoutRules) throws VcsException {
    if (checkoutRules.getExcludeRules().size() != 0) {
      throw new VcsException(
        "The exclude rules are not supported for agent checkout for the git (" + checkoutRules.getExcludeRules().size() +
        " rule(s) detected) for VCS Root " + root.getName());
    }
    if (checkoutRules.getIncludeRules().size() > 1) {
      throw new VcsException(
        "At most one include rule is supported for agent checkout for the git (" + checkoutRules.getIncludeRules().size() +
        " rule(s) detected) for VCS Root " + root.getName());
    }
    if (checkoutRules.getIncludeRules().size() == 1) {
      IncludeRule ir = checkoutRules.getIncludeRules().get(0);
      if (!".".equals(ir.getFrom()) && ir.getFrom().length() != 0) {
        throw new VcsException("The include rule must have a form '. => subdir' (" + ir.toDescriptiveString() +
                               ") for VCS Root " + root.getName());
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
