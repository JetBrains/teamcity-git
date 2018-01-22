/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;

/**
 * Agent Git plugin settings class
 */
public class AgentGitVcsRoot extends GitVcsRoot {
  /**
   * The clean policy for the agent
   */
  private final AgentCleanPolicy myCleanPolicy;
  /**
   * The policy for cleaning files
   */
  private final AgentCleanFilesPolicy myCleanFilesPolicy;
  /**
   * Local repository directory
   */
  private final File myLocalRepositoryDir;


  public AgentGitVcsRoot(MirrorManager mirrorManager, File localRepositoryDir, VcsRoot root) throws VcsException {
    super(mirrorManager, root);
    myLocalRepositoryDir = localRepositoryDir;
    String clean = getProperty(Constants.AGENT_CLEAN_POLICY);
    myCleanPolicy = clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
    String cleanFiles = getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
    myCleanFilesPolicy = cleanFiles == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(cleanFiles);
  }

  /**
   * @return the clean policy for the agent
   */
  public AgentCleanPolicy getCleanPolicy() {
    return myCleanPolicy;
  }

  /**
   * @return specifies which files should be cleaned after checkout
   */
  public AgentCleanFilesPolicy getCleanFilesPolicy() {
    return myCleanFilesPolicy;
  }

  /**
   * @return the local repository directory
   */
  public File getLocalRepositoryDir() {
    return myLocalRepositoryDir;
  }

  public File getRepositoryDir() {
    //ignore custom clone path on server
    String fetchUrl = getRepositoryFetchURL().toString();
    return myMirrorManager.getMirrorDir(fetchUrl);
  }

  /**
   * @return debug information
   */
  public String debugInfo() {
    return "(" + getName() + ", " + getLocalRepositoryDir() + "," + getRepositoryFetchURL().toString() + ")";
  }
}
