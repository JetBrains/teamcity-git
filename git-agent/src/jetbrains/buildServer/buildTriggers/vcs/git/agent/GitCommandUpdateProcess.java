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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * The update process that uses C git.
 */
public class GitCommandUpdateProcess extends GitUpdateProcess {
  /**
   * The ssh service to use
   */
  final GitAgentSSHService mySshService;
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


  public GitCommandUpdateProcess(@NotNull BuildAgentConfiguration agentConfiguration,
                                 @NotNull SmartDirectoryCleaner directoryCleaner,
                                 @NotNull GitAgentSSHService sshService,
                                 @NotNull GitPathResolver gitPathResolver,
                                 @NotNull VcsRoot root,
                                 @NotNull CheckoutRules checkoutRules,
                                 @NotNull String toVersion,
                                 @NotNull File checkoutDirectory,
                                 @NotNull AgentRunningBuild build)
    throws VcsException {
    super(agentConfiguration, directoryCleaner, root, checkoutRules, toVersion, checkoutDirectory, build.getBuildLogger(),
          getGitPath(root, agentConfiguration, gitPathResolver, build), isUseNativeSSH(build));
    mySshService = sshService;
  }


  private static boolean isUseNativeSSH(AgentRunningBuild runningBuild) {
    String value = runningBuild.getSharedConfigParameters().get("teamcity.git.use.native.ssh");
    return "true".equals(value);
  }

  /**
   * Check if the git could be run
   *
   * @param path the path to use
   * @throws VcsException if there is a problem with running git or git has invalid version
   */
  private static void canRun(@NotNull String path) throws VcsException {
    GitVersion v;
    try {
      v = new VersionCommand(path).version();
    } catch (VcsException e) {
      throw new VcsException("Unable to run git at path " + path, e);
    }
    if (!GitVersion.MIN.isLessOrEqual(v)) {
      throw new VcsException("TeamCity supports Git version 1.6.4.0 or higher, found Git ("+ path +") has version " + v +
                             ". Please upgrade Git or use server-side checkout.");
    }
  }

  private static String getGitPath(VcsRoot root,
                                   final BuildAgentConfiguration agentConfiguration,
                                   GitPathResolver gitPathResolver,
                                   @NotNull AgentRunningBuild build) throws VcsException {
    String path = root.getProperty(Constants.AGENT_GIT_PATH);
    if (path != null) {
      path = gitPathResolver.resolveGitPath(agentConfiguration, path);
      Loggers.VCS.info("Using vcs root's git: " + path);
    } else {
      path = build.getSharedBuildParameters().getEnvironmentVariables().get(Constants.GIT_PATH_ENV);
      if (path != null) {
        Loggers.VCS.info("Using git specified by " + Constants.GIT_PATH_ENV + ": " + path);
      } else {
        path = defaultGit();
        Loggers.VCS.info("Using default git: " + path);
      }
    }
    canRun(path);
    return path;
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
      Loggers.VCS.info("Trying default git location: " + f.getPath());
      if (f.exists()) {
        return f.getAbsolutePath();
      }
    }
    Loggers.VCS.info(String.format("The git has not been found in default locations. Will use '%s' command without specified path.",
                                   SystemInfo.isWindows ? DEFAULT_WINDOWS_GIT : DEFAULT_UNIX_GIT));
    return SystemInfo.isWindows ? DEFAULT_WINDOWS_GIT : DEFAULT_UNIX_GIT;
  }

  /**
   * {@inheritDoc}
   */
  protected void addRemote(final String name, final URIish fetchUrl) throws VcsException {
    new RemoteCommand(mySettings).add(name, fetchUrl.toString());
  }

  @Override
  protected void addRemoteBare(String name, URIish fetchUrl) throws VcsException {
    new RemoteCommand(mySettings, mySettings.getRepositoryDir().getAbsolutePath()).add(name, fetchUrl.toString());
  }

  /**
   * {@inheritDoc}
   */
  protected void init() throws VcsException {
    new InitCommand(mySettings).init();
  }

  protected void initBare() throws VcsException {
    File bareRepositoryDir = mySettings.getRepositoryDir();
    new InitCommand(mySettings).initBare(bareRepositoryDir.getAbsolutePath());
  }

  /**
   * {@inheritDoc}
   */
  protected BranchInfo getBranchInfo(final String branch) throws VcsException {
    return new BranchCommand(mySettings).branchInfo(branch);
  }

  /**
   * {@inheritDoc}
   */
  protected String getConfigProperty(final String propertyName) throws VcsException {
    return new ConfigCommand(mySettings).get(propertyName);
  }

  /**
   * {@inheritDoc}
   */
  protected void setConfigProperty(final String propertyName, final String value) throws VcsException {
    new ConfigCommand(mySettings).set(propertyName, value);
  }

  @Override
  protected void setConfigPropertyBare(String propertyName, String value) throws VcsException {
    new ConfigCommand(mySettings, mySettings.getRepositoryDir().getAbsolutePath()).set(propertyName, value);
  }

  protected void hardReset() throws VcsException {
    new ResetCommand(mySettings).hardReset(revision);
  }

  /**
   * {@inheritDoc}
   */
  protected void doClean(BranchInfo branchInfo) throws VcsException {
    if (mySettings.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && mySettings.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      mLogger.message("Cleaning " + myRoot.getName() + " in " + myDirectory + " the file set " + mySettings.getCleanFilesPolicy());
      new CleanCommand(mySettings).clean();
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void forceCheckout() throws VcsException {
    new BranchCommand(mySettings).forceCheckout(mySettings.getBranch());
  }

  /**
   * {@inheritDoc}
   */
  protected void setBranchCommit() throws VcsException {
    new BranchCommand(mySettings).setBranchCommit(mySettings.getBranch(), revision);
  }

  /**
   * {@inheritDoc}
   */
  protected void createBranch() throws VcsException {
    new BranchCommand(mySettings).createBranch(mySettings.getBranch(), GitUtils.remotesBranchRef(mySettings.getBranch()));
  }

  /**
   * {@inheritDoc}
   */
  protected void fetch() throws VcsException {
    new FetchCommand(mySettings, mySshService).fetch();
  }

  @Override
  protected void fetchBare() throws VcsException {
    new FetchCommand(mySettings, mySshService, mySettings.getRepositoryDir().getAbsolutePath()).fetch();
  }

  /**
   * {@inheritDoc}
   */
  protected String checkRevision(final String revision, String... errorsLogLevel) {
    return new LogCommand(mySettings).checkRevision(revision, errorsLogLevel);
  }

  /**
   * {@inheritDoc}
   */
  protected void doSubmoduleUpdate(File directory) throws VcsException {
    File gitmodules = new File(directory, ".gitmodules");
    if (gitmodules.exists()) {
      mLogger.message("Checkout submodules in " + directory);
      SubmoduleCommand submoduleCommand = new SubmoduleCommand(mySettings, mySshService, directory.getAbsolutePath());
      submoduleCommand.init();
      submoduleCommand.update();

      if (recursiveSubmoduleCheckout()) {
        try {
          String gitmodulesContents = FileUtil.readText(gitmodules);
          Config config = new Config();
          config.fromText(gitmodulesContents);

          Set<String> submodules = config.getSubsections("submodule");
          for (String submoduleName : submodules) {
            String submodulePath = config.getString("submodule", submoduleName, "path");
            doSubmoduleUpdate(new File(directory, submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator))));
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
}
