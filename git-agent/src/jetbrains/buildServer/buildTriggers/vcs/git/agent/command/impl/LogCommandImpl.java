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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LogCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LogCommandImpl extends BaseCommandImpl implements LogCommand {

  private String myStartPoint;
  private int myCommitsNumber;
  private String myFormat;

  public LogCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public LogCommand setStartPoint(@NotNull String startPoint) {
    myStartPoint = startPoint;
    return this;
  }

  @NotNull
  public LogCommand setCommitsNumber(int commitsNumber) {
    myCommitsNumber = commitsNumber;
    return this;
  }

  @NotNull
  public LogCommand setPrettyFormat(@NotNull String format) {
    myFormat = format;
    return this;
  }

  @Nullable
  public String call() {
    try {
      GitCommandLine cmd = getCmd();
      cmd.addParameters("log");
      if (myCommitsNumber != 0)
        cmd.addParameter("-n" + myCommitsNumber);
      if (myFormat != null)
        cmd.addParameter("--pretty=format:" + myFormat);
      cmd.addParameter(myStartPoint);
      cmd.addParameter("--");
      ExecResult r = CommandUtil.runCommand(cmd);
      CommandUtil.failIfNotEmptyStdErr(cmd, r);
      return r.getStdout().trim();
    } catch (VcsException e) {
      return null;
    }
  }
}
