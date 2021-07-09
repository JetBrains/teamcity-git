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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SubmoduleUpdateCommandImpl extends BaseAuthCommandImpl<SubmoduleUpdateCommand> implements SubmoduleUpdateCommand {

  private boolean myForce;
  private Integer myDepth;

  public SubmoduleUpdateCommandImpl(@NotNull AgentGitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public SubmoduleUpdateCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  @Override
  public SubmoduleUpdateCommand setDepth(final int depth) {
    myDepth = depth;
    return this;
  }

  public void call() throws VcsException {
    AgentGitCommandLine cmd = getCmd();
    cmd.addParameter("submodule");
    cmd.addParameter("update");
    if (myForce)
      cmd.addParameter("--force");
    if (myDepth != null) {
      cmd.addParameter("--depth=" + myDepth);
    }
    runCmd(cmd);
  }
}
