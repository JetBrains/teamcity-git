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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * The update process that uses C git.
 */
public class GitCommandUpdateProcess extends GitUpdateProcess {

  /** Git version which supports --progress option in the fetch command */
  private final static GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);
  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours

  private final GitAgentSSHService mySshService;
  private final AgentPluginConfig myPluginConfig;

  public GitCommandUpdateProcess(@NotNull SmartDirectoryCleaner directoryCleaner,
                                 @NotNull GitAgentSSHService sshService,
                                 @NotNull VcsRoot root,
                                 @NotNull CheckoutRules checkoutRules,
                                 @NotNull String toVersion,
                                 @NotNull File checkoutDirectory,
                                 @NotNull AgentRunningBuild build,
                                 @NotNull AgentPluginConfig pluginConfig)
    throws VcsException {
    super(directoryCleaner, root, checkoutRules, toVersion, checkoutDirectory, build.getBuildLogger(), pluginConfig);
    mySshService = sshService;
    myPluginConfig = pluginConfig;
  }


  protected void addRemote(final String name, final URIish fetchUrl) throws VcsException {
    new RemoteCommand(mySettings).add(name, fetchUrl.toString());
  }

  @Override
  protected void addRemoteBare(String name, URIish fetchUrl) throws VcsException {
    new RemoteCommand(mySettings, mySettings.getRepositoryDir().getAbsolutePath()).add(name, fetchUrl.toString());
  }

  protected void init() throws VcsException {
    new InitCommand(mySettings).init();
  }

  protected void initBare() throws VcsException {
    File bareRepositoryDir = mySettings.getRepositoryDir();
    new InitCommand(mySettings).initBare(bareRepositoryDir.getAbsolutePath());
  }

  protected BranchInfo getBranchInfo(final String branch) throws VcsException {
    return new BranchCommand(mySettings).branchInfo(branch);
  }


  protected String getConfigProperty(final String propertyName) throws VcsException {
    return new ConfigCommand(mySettings).get(propertyName);
  }


  protected void setConfigProperty(final String propertyName, final String value) throws VcsException {
    new ConfigCommand(mySettings).set(propertyName, value);
  }


  protected void hardReset() throws VcsException {
    new ResetCommand(mySettings).hardReset(myRevision);
  }


  protected void doClean(BranchInfo branchInfo) throws VcsException {
    if (mySettings.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        (!branchInfo.isCurrent && mySettings.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE)) {
      myLogger.message("Cleaning " + myRoot.getName() + " in " + myDirectory + " the file set " + mySettings.getCleanFilesPolicy());
      new CleanCommand(mySettings).clean();
    }
  }

  protected void forceCheckout() throws VcsException {
    new BranchCommand(mySettings).forceCheckout(mySettings.getRef());
  }

  protected void setBranchCommit() throws VcsException {
    new BranchCommand(mySettings).setBranchCommit(mySettings.getRef(), myRevision);
  }

  protected void createBranch() throws VcsException {
    new BranchCommand(mySettings).createBranch(mySettings.getRef(), GitUtils.createRemoteRef(mySettings.getRef()));
  }

  protected void fetch() throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);
    new FetchCommand(mySettings, mySshService, timeout).fetch(silent);
  }

  @Override
  protected void fetchBare() throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);
    new FetchCommand(mySettings, mySshService, mySettings.getRepositoryDir().getAbsolutePath(), timeout).fetch(silent);
  }

  private boolean isSilentFetch() {
    GitVersion version = myPluginConfig.getGitVersion();
    return GIT_WITH_PROGRESS_VERSION.isGreaterThan(version);
  }

  protected String checkRevision(final String revision, String... errorsLogLevel) {
    return new LogCommand(mySettings).checkRevision(revision, errorsLogLevel);
  }

  protected void doSubmoduleUpdate(File directory) throws VcsException {
    File gitmodules = new File(directory, ".gitmodules");
    if (gitmodules.exists()) {
      myLogger.message("Checkout submodules in " + directory);
      SubmoduleCommand submoduleCommand = new SubmoduleCommand(mySettings, mySshService, directory.getAbsolutePath(), SILENT_TIMEOUT);
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

  private int getTimeout(boolean silentFetch) {
    if (silentFetch)
      return SILENT_TIMEOUT;
    else
      return myPluginConfig.getIdleTimeoutSeconds();
  }
}
