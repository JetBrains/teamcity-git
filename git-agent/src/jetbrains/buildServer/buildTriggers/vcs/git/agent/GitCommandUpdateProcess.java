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
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.parameters.AgentParameterResolverFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.parameters.CompositeParametersProvider;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.parameters.impl.CompositeParametersProviderImpl;
import jetbrains.buildServer.parameters.impl.DynamicContextVariables;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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

  /**
   * The constructor
   *
   * @param agentConfiguration the configuration for this agent
   * @param directoryCleaner   the directory cleaner
   * @param sshService         the used ssh service
   * @param resolverFactory    the resolver used to resolve path to git
   * @param buildTracker       the build tracker
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
                                 @NotNull AgentParameterResolverFactory resolverFactory,
                                 @NotNull CurrentBuildTracker buildTracker,
                                 @NotNull VcsRoot root,
                                 @NotNull CheckoutRules checkoutRules,
                                 @NotNull String toVersion,
                                 @NotNull File checkoutDirectory,
                                 @NotNull BuildProgressLogger logger)
    throws VcsException {
    super(agentConfiguration, directoryCleaner, root, checkoutRules, toVersion, checkoutDirectory, logger,
          getGitPath(root, agentConfiguration, resolverFactory, buildTracker));
    mySshService = sshService;
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
      throw new VcsException("Unsupported version of Git is detected at (" + path + "): " + v);
    }
  }

  /**
   * Get git path from root
   *
   * @param root               the vcs root
   * @param agentConfiguration the agent configuration
   * @param resolverFactory    the resolver factory
   * @param buildTracker       the build tracker
   * @return the path to the git executable or null if neither configured nor found
   * @throws VcsException if git could not be found or has invalid version
   */
  private static String getGitPath(VcsRoot root,
                                   final BuildAgentConfiguration agentConfiguration,
                                   AgentParameterResolverFactory resolverFactory,
                                   CurrentBuildTracker buildTracker) throws VcsException {
    String path = root.getProperty(Constants.AGENT_GIT_PATH);
    if (path != null) {
      ValueResolver valueResolver = getValueResolver(agentConfiguration, resolverFactory);
      ProcessingResult result = valueResolver.resolve(path);
      if (!result.isFullyResolved()) {
        throw new VcsException("The value is not fully resolved: " + result.getResult());
      }
      path = result.getResult();
      Loggers.VCS.info("Using vcs root's git: " + path);
    } else {
      path = buildTracker.getCurrentBuild().getBuildParameters().getEnvironmentVariables().get(Constants.GIT_PATH_ENV);
      if (path != null) {
        Loggers.VCS.info("Using git specified by " + Constants.GIT_PATH_ENV + ": " + path);
      } else {
        path = defaultGit();
        if (path == null) {
          throw new VcsException("The git has not been found.");
        } else {
          Loggers.VCS.info("Using default git: " + path);
        }
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
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected void addRemote(final String name, final URIish fetchUrl) throws VcsException {
    new RemoteCommand(mySettings).add(name, fetchUrl.toString());
  }

  /**
   * {@inheritDoc}
   */
  protected void init() throws VcsException {
    new InitCommand(mySettings).init();
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

  /**
   * {@inheritDoc}
   */
  protected String checkRevision(final String revision) {
    return new LogCommand(mySettings).checkRevision(revision);
  }

  /**
   * Create a value resolver
   *
   * @param agentConfiguration the build agent configuration
   * @param resolverFactory    the resolver factory
   * @return a created value resolver
   */
  private static ValueResolver getValueResolver(final BuildAgentConfiguration agentConfiguration,
                                                final AgentParameterResolverFactory resolverFactory) {
    final CompositeParametersProvider parametersProvider = new CompositeParametersProviderImpl();
    parametersProvider
      .appendParametersProvider(new MapParametersProviderImpl("agent parameters", agentConfiguration.getAgentParameters()));
    parametersProvider
      .appendParametersProvider(new MapParametersProviderImpl("agent custom parameters", agentConfiguration.getCustomProperties()));

    final DynamicContextVariables variables = new DynamicContextVariables();

    return resolverFactory.createValueResolver(variables, parametersProvider);
  }
}
