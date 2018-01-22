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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.GetConfigCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GetConfigCommandImpl extends BaseCommandImpl implements GetConfigCommand {

  private String myName;

  public GetConfigCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public GetConfigCommand setPropertyName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public String call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("config", myName);
    ExecResult r = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, r);
    return r.getStdout().trim();
  }
}
