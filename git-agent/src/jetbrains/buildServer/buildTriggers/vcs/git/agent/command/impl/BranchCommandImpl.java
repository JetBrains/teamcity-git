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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.BranchInfo;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.BranchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class BranchCommandImpl implements BranchCommand {

  private final GeneralCommandLine myCmd;
  private String myBranch;

  public BranchCommandImpl(@NotNull GeneralCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public BranchCommand setBranch(@NotNull String branch) {
    myBranch = branch;
    return this;
  }

  @NotNull
  public BranchInfo call() throws VcsException {
    myCmd.addParameter("branch");
    ExecResult r = CommandUtil.runCommand(myCmd);
    CommandUtil.failIfNotEmptyStdErr(myCmd, r);
    return parseOutput(r.getStdout());
  }

  @NotNull
  private BranchInfo parseOutput(String out) {
    for (String line : out.split("\n")) {
      if (line.length() < 2)
        continue;
      String b = line.substring(2).trim();
      if (b.equals(myBranch))
        return new BranchInfo(true, line.charAt(0) == '*');
    }
    return new BranchInfo(false, false);
  }
}
