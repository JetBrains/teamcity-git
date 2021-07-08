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

import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.RepackCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RepackCommandImpl extends BaseCommandImpl implements RepackCommand {

  public RepackCommandImpl(@NotNull AgentGitCommandLine cmd) {
    super(cmd);
  }

  public void call() throws VcsException {
    AgentGitCommandLine cmd = getCmd();
    cmd.addParameters("repack", "-a", "-d");
    CommandUtil.runCommand(cmd);
  }
}
