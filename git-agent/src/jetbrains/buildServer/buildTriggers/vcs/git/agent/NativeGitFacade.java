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

import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
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
public class NativeGitFacade implements GitFacade {

  /** Git version which supports --progress option in the fetch command */
  private final static GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);
  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours

  private final AgentPluginConfig myPluginConfig;
  private final GitAgentSSHService mySshService;
  protected final BuildProgressLogger myLogger;

  public NativeGitFacade(@NotNull AgentPluginConfig pluginConfig,
                         @NotNull GitAgentSSHService sshService,
                         @NotNull BuildProgressLogger logger) throws VcsException {
    myPluginConfig = pluginConfig;
    mySshService = sshService;
    myLogger = logger;
  }


  public void addRemote(@NotNull final AgentSettings settings, final String name, final URIish fetchUrl) throws VcsException {
    new RemoteCommand(settings).add(name, fetchUrl.toString());
  }

  public void addRemoteBare(@NotNull final AgentSettings settings, String name, URIish fetchUrl) throws VcsException {
    new RemoteCommand(settings, settings.getRepositoryDir().getAbsolutePath()).add(name, fetchUrl.toString());
  }

  public void init(@NotNull final AgentSettings settings) throws VcsException {
    new InitCommand(settings).init();
  }

  public void initBare(@NotNull final AgentSettings settings) throws VcsException {
    File bareRepositoryDir = settings.getRepositoryDir();
    new InitCommand(settings).initBare(bareRepositoryDir.getAbsolutePath());
  }

  public BranchInfo getBranchInfo(@NotNull final AgentSettings settings, final String branch) throws VcsException {
    return new BranchCommand(settings).branchInfo(branch);
  }


  public String getConfigProperty(@NotNull final AgentSettings settings, final String propertyName) throws VcsException {
    return new ConfigCommand(settings).get(propertyName);
  }


  public void setConfigProperty(@NotNull final AgentSettings settings, final String propertyName, final String value) throws VcsException {
    new ConfigCommand(settings).set(propertyName, value);
  }


  public void hardReset(@NotNull final AgentSettings settings, @NotNull final String revision) throws VcsException {
    new ResetCommand(settings).hardReset(revision);
  }


  public void clean(@NotNull final AgentSettings settings, BranchInfo branchInfo) throws VcsException {
    new CleanCommand(settings).clean();
  }

  public void forceCheckout(@NotNull final AgentSettings settings, @NotNull final String ref) throws VcsException {
    new BranchCommand(settings).forceCheckout(ref);
  }

  public void setBranchCommit(@NotNull final AgentSettings settings, @NotNull final String branchRef, @NotNull final String revision) throws VcsException {
    new BranchCommand(settings).setBranchCommit(branchRef, revision);
  }

  public void createBranch(@NotNull final AgentSettings settings, @NotNull final String branchRef) throws VcsException {
    new BranchCommand(settings).createBranch(settings.getRef(), GitUtils.createRemoteRef(settings.getRef()));
  }

  public void fetch(@NotNull final AgentSettings settings) throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);
    FetchCommand command = new FetchCommand(settings, mySshService, timeout);
    command.fetch(silent);
  }

  public void fetchBare(@NotNull final AgentSettings settings) throws VcsException {
    boolean silent = isSilentFetch();
    int timeout = getTimeout(silent);
    new FetchCommand(settings, mySshService, settings.getRepositoryDir().getAbsolutePath(), timeout).fetch(silent);
  }

  private boolean isSilentFetch() {
    GitVersion version = myPluginConfig.getGitVersion();
    return GIT_WITH_PROGRESS_VERSION.isGreaterThan(version);
  }

  public String checkRevision(@NotNull final AgentSettings settings, final String revision, String... errorsLogLevel) {
    return new LogCommand(settings).checkRevision(revision, errorsLogLevel);
  }

  public void doSubmoduleUpdate(@NotNull final AgentSettings settings, File directory) throws VcsException {
    File gitmodules = new File(directory, ".gitmodules");
    if (gitmodules.exists()) {
      myLogger.message("Checkout submodules in " + directory);
      SubmoduleCommand submoduleCommand = new SubmoduleCommand(settings, mySshService, directory.getAbsolutePath(), SILENT_TIMEOUT);
      submoduleCommand.init();
      submoduleCommand.update();

      if (recursiveSubmoduleCheckout(settings)) {
        try {
          String gitmodulesContents = FileUtil.readText(gitmodules);
          Config config = new Config();
          config.fromText(gitmodulesContents);

          Set<String> submodules = config.getSubsections("submodule");
          for (String submoduleName : submodules) {
            String submodulePath = config.getString("submodule", submoduleName, "path");
            doSubmoduleUpdate(settings, new File(directory, submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator))));
          }
        } catch (IOException e) {
          throw new VcsException("Error while reading " + gitmodules, e);
        } catch (ConfigInvalidException e) {
          throw new VcsException("Error while parsing " + gitmodules, e);
        }
      }
    }
  }

  private boolean recursiveSubmoduleCheckout(@NotNull final AgentSettings settings) {
    return SubmodulesCheckoutPolicy.CHECKOUT.equals(settings.getSubmodulesCheckoutPolicy()) ||
           SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS.equals(settings.getSubmodulesCheckoutPolicy());
  }

  private int getTimeout(boolean silentFetch) {
    if (silentFetch)
      return SILENT_TIMEOUT;
    else
      return myPluginConfig.getIdleTimeoutSeconds();
  }
}
