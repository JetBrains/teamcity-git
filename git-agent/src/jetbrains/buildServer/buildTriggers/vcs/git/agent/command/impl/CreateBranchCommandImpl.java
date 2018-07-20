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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CreateBranchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CreateBranchCommandImpl extends BaseCommandImpl implements CreateBranchCommand {

  private String myName;
  private String myStartPoint;
  private boolean myTrack = false;

  public CreateBranchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public CreateBranchCommand setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public CreateBranchCommand setStartPoint(@NotNull String startPoint) {
    myStartPoint = startPoint;
    return this;
  }

  @NotNull
  public CreateBranchCommand setTrack(boolean track) {
    myTrack = track;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("branch");
    cmd.addParameter("--create-reflog");
    if (myTrack) {
      cmd.addParameter("--track");
    } else {
      cmd.addParameter("--no-track");
    }
    cmd.addParameter(myName);
    cmd.addParameter(myStartPoint);
    ExecResult result = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, result);
  }
}
