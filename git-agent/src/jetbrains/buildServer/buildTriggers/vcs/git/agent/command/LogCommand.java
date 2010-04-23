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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * The "git log" command
 */
public class LogCommand extends RepositoryCommand {
  /**
   * The logger class
   */
  private final static Logger LOG = Logger.getLogger(LogCommand.class);

  /**
   * The constructor
   *
   * @param settings the git settings
   */
  public LogCommand(@NotNull final AgentSettings settings) {
    super(settings);
  }

  /**
   * Check if the revision is already available
   *
   * @param revision the revision to check
   * @return A string describing revision, or null if revision is not found
   */
  public String checkRevision(String revision) {
    try {
      GeneralCommandLine cmd = createCommandLine();
      cmd.addParameters("log", "-n1", "--pretty=format:%H%x20%s", revision, "--");
      ExecResult r = runCommand(cmd);
      failIfNotEmptyStdErr(cmd, r);
      return r.getStdout();
    } catch (VcsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Failed to retrieve revision " + revision + " from " + getSettings().debugInfo());
      }
      return null;
    }
  }
}
