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

import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanFilesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;

/**
 * Agent Git plugin settings class
 */
public class AgentSettings extends Settings {
  /**
   * The name of the root
   */
  private final String rootName;
  /**
   * The clean policy for the agent
   */
  private final AgentCleanPolicy cleanPolicy;
  /**
   * The policy for cleaning files
   */
  private final AgentCleanFilesPolicy cleanFilesPolicy;
  /**
   * The path to the git command
   */
  private final String gitCommandPath;
  /**
   * Local repository directory
   */
  private final File localRepositoryDir;

  private final boolean useNativeSSH;

  /**
   * The constructor
   *
   * @param cacheDir dir where bare repositories are stored
   * @param gitCommandPath     the path to the git
   * @param localRepositoryDir the local directory
   * @param root               the VCS root to get settings from
   * @throws VcsException if there is a configuration problem
   */
  public AgentSettings(File cacheDir, String gitCommandPath, File localRepositoryDir, VcsRoot root, boolean useNativeSSH) throws VcsException {
    super(root, cacheDir);
    this.gitCommandPath = gitCommandPath;
    this.localRepositoryDir = localRepositoryDir;
    this.rootName = root.getName();
    String clean = root.getProperty(Constants.AGENT_CLEAN_POLICY);
    this.cleanPolicy = clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
    String cleanFiles = root.getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
    this.cleanFilesPolicy = cleanFiles == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(cleanFiles);
    this.useNativeSSH = useNativeSSH;
  }

  /**
   * @return the clean policy for the agent
   */
  public AgentCleanPolicy getCleanPolicy() {
    return cleanPolicy;
  }

  /**
   * @return specifies which files should be cleaned after checkout
   */
  public AgentCleanFilesPolicy getCleanFilesPolicy() {
    return cleanFilesPolicy;
  }

  /**
   * @return the local repository directory
   */
  public File getLocalRepositoryDir() {
    return localRepositoryDir;
  }

  /**
   * @return the path to the command line git
   */
  public String getGitCommandPath() {
    return gitCommandPath;
  }

  public boolean isUseNativeSSH() {
    return useNativeSSH;
  }

  /**
   * @return debug information
   */
  public String debugInfo() {
    return "(" + rootName + ", " + getLocalRepositoryDir() + "," + getRepositoryFetchURL().toString() + ")";
  }
}
