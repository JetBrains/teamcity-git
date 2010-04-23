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
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The 'git clean' command
 */
public class CleanCommand extends BaseCommand {
  /**
   * The constructor
   *
   * @param settings the settings object
   */
  public CleanCommand(@NotNull final AgentSettings settings) {
    super(settings);
  }

  /**
   * Clean the working tree
   * @throws VcsException if git could not be launched
   */
  public void clean() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("clean", "-f", "-d");
    switch(getSettings().getCleanFilesPolicy()) {
      case ALL_UNTRACKED:
        cmd.addParameter("-x");
        break;
      case IGNORED_ONLY:
        cmd.addParameter("-X");
        break;
      case NON_IGNORED_ONLY:
        break;
      default:
        throw new IllegalStateException("Unsupported policy: "+getSettings().getCleanFilesPolicy());
    }
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }
}
