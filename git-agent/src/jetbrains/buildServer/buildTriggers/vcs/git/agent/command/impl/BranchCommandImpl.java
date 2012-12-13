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

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.BranchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Branches;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * @author dmitry.neverov
 */
public class BranchCommandImpl implements BranchCommand {

  private final GitCommandLine myCmd;

  public BranchCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public Branches call() throws VcsException {
    myCmd.addParameter("branch");
    ExecResult r = CommandUtil.runCommand(myCmd);
    CommandUtil.failIfNotEmptyStdErr(myCmd, r);
    return parseOutput(r.getStdout());
  }

  @NotNull
  private Branches parseOutput(String out) {
    Branches branches = new Branches();
    for (String l : StringUtil.splitByLines(out)) {
      String line = l.trim();
      if (isEmpty(line))
        continue;
      boolean currentBranch = line.startsWith("* ");
      String branchName = currentBranch ? line.substring(2).trim() : line;
      branches.addBranch(branchName, currentBranch);
    }
    return branches;
  }
}
