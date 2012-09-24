/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LogCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class LogCommandImpl implements LogCommand {

  private final GitCommandLine myCmd;
  private String myStartPoint;
  private int myCommitsNumber;
  private String myFormat;

  public LogCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
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
      myCmd.addParameters("log");
      if (myCommitsNumber != 0)
        myCmd.addParameter("-n" + myCommitsNumber);
      if (myFormat != null)
        myCmd.addParameter("--pretty=format:" + myFormat);
      myCmd.addParameter(myStartPoint);
      myCmd.addParameter("--");
      ExecResult r = CommandUtil.runCommand(myCmd);
      CommandUtil.failIfNotEmptyStdErr(myCmd, r);
      return r.getStdout().trim();
    } catch (VcsException e) {
      return null;
    }
  }
}
