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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import java.io.File;

/**
 * The basic command settings
 */
public class CommandSettings {
  /**
   * The path to the git command
   */
  private final String gitCommandPath;
  /**
   * Local repository directory
   */
  private final File localRepositoryDir;

  public CommandSettings(String gitCommandPath, File localRepositoryDir) {
    this.gitCommandPath = gitCommandPath;
    this.localRepositoryDir = localRepositoryDir;
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
}
