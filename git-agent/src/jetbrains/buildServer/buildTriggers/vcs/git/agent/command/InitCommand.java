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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.vcs.VcsException;

/**
 * The "git init" command
 */
public class InitCommand extends RepositoryCommand {
  /**
   * The constructor
   *
   * @param s the settings object
   */
  public InitCommand(AgentSettings s) {
    super(s);
  }

  /**
   * Initialize git directory
   *
   * @throws VcsException if initializing fails
   */
  public void init() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("init");
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }
}
