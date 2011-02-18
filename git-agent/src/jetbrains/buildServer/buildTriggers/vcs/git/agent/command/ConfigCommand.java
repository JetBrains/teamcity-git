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
import org.jetbrains.annotations.NotNull;

/**
 * The wrapper for "git config" command.
 */
public class ConfigCommand extends RepositoryCommand {
  /**
   * The git config command
   *
   * @param settings the settings object
   */
  public ConfigCommand(@NotNull final AgentSettings settings) {
    super(settings);
  }

  public ConfigCommand(@NotNull final AgentSettings settings, String bareRepositoryPath) {
    super(settings, bareRepositoryPath);
  }

  /**
   * Get configuration property value
   *
   * @param propertyName the property name
   * @return the property value
   * @throws VcsException if the property could not be found
   */
  public String get(String propertyName) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("config", propertyName);
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
    return r.getStdout().trim();
  }

  /**
   * Set configuration property value
   *
   * @param propertyName the property name
   * @param value        the property value
   * @throws VcsException if the property could not be found
   */
  public void set(String propertyName, String value) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("config", propertyName, value);
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }
}
