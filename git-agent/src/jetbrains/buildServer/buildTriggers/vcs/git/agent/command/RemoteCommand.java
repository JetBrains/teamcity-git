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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The "git remote" command
 */
public class RemoteCommand extends RepositoryCommand {

  /**
   * The constructor
   *
   * @param settings the settings object
   */
  public RemoteCommand(@NotNull final AgentSettings settings) {
    super(settings);
  }

  /**
   * Configure remote
   *
   * @param name the remote name
   * @param url  the remote URL
   * @throws VcsException if there is a problem with running git
   */
  public void add(String name, String url) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("remote", "add", name, url);
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }
}
