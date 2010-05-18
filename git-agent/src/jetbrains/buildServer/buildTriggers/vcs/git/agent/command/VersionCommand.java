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

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitVersion;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The "git version" command
 */
public class VersionCommand extends BaseCommand {
  /**
   * The constructor
   *
   * @param gitPath the path to git
   */
  public VersionCommand(@NotNull String gitPath) {
    super(gitPath, ".");
  }

  /**
   * @return the current version for the git
   * @throws VcsException if there is a problem with running git
   */
  public GitVersion version() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("version");
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
    return GitVersion.parse(r.getStdout());
  }
}
