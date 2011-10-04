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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public interface GitFacade {

  /**
   * Add remote to the repository
   *
   * @param name     the remote name
   * @param fetchUrl the fetch URL
   * @throws jetbrains.buildServer.vcs.VcsException if repository cannot be accessed
   */
  void addRemote(@NotNull AgentSettings settings, String name, URIish fetchUrl) throws VcsException;

  void addRemoteBare(@NotNull AgentSettings settings, String name, URIish fetchUrl) throws VcsException;

  /**
   * Init repository
   *
   * @throws VcsException if repository cannot be accessed
   */
  void init(@NotNull AgentSettings settings) throws VcsException;

  void initBare(@NotNull AgentSettings settings) throws VcsException;


  /**
   * Create branch
   *
   * @param branch the branch name
   * @return information about the branch
   * @throws VcsException if branch information could not be retrieved
   */
  BranchInfo getBranchInfo(@NotNull AgentSettings settings, String branch) throws VcsException;

  /**
   * Get configuration property
   *
   * @param propertyName the property name
   * @return the property value
   * @throws VcsException if there is problem with getting property
   */
  String getConfigProperty(@NotNull AgentSettings settings, String propertyName) throws VcsException;

  /**
   * Set configuration property value
   *
   * @param propertyName the property name
   * @param value        the property value
   * @throws VcsException if the property could not be set
   */
  void setConfigProperty(@NotNull AgentSettings settings, String propertyName, String value) throws VcsException;

  /**
   * Hard reset to the specified revision
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  void hardReset(@NotNull AgentSettings settings, @NotNull String revision) throws VcsException;

  /**
   * Perform clean according to the settings
   *
   * @param branchInfo the branch information to use
   * @throws VcsException if there is a problem with accessing repository
   */
  void clean(@NotNull AgentSettings settings, BranchInfo branchInfo) throws VcsException;

  /**
   * Force checkout of the branch removing files that are no more under VCS
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  void forceCheckout(@NotNull AgentSettings settings, @NotNull String ref) throws VcsException;

  /**
   * Set commit on non-active branch
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  void setBranchCommit(@NotNull AgentSettings settings, @NotNull String branchRef, @NotNull String revision) throws VcsException;

  /**
   * Create branch
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  void createBranch(@NotNull AgentSettings settings, @NotNull String branchRef) throws VcsException;

  /**
   * Perform fetch operation
   *
   * @throws VcsException if there is a problem with accessing repository
   */
  void fetch(@NotNull AgentSettings settings) throws VcsException;

  void fetchBare(@NotNull AgentSettings settings) throws VcsException;

  /**
   * Check the specified revision
   *
   * @param revision       the revision expression to check
   * @param errorsLogLevel log level to use for reporting errors of native git command
   * @return a short revision information or null if revision is not found
   */
  String checkRevision(@NotNull AgentSettings settings, final String revision, final String... errorsLogLevel);

  /**
   * Make submodule init and submodule update
   *
   * @throws VcsException
   */
  void doSubmoduleUpdate(@NotNull AgentSettings settings, File dir) throws VcsException;

}
