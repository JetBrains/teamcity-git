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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SetUpstreamCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SetUpstreamCommandImpl extends BaseCommandImpl implements SetUpstreamCommand {
  private String myLocalBranch;
  private String myUpstreamBranch;

  public SetUpstreamCommandImpl(@NotNull GitCommandLine cmd,
                                @NotNull String localBranch,
                                @NotNull String upstreamBranch) {
    super(cmd);
    myLocalBranch = localBranch;
    myUpstreamBranch = upstreamBranch;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    GitVersion version = cmd.getGitVersion();
    if (version.isLessThan(new GitVersion(1, 7, 0))) {
      //ability to set upstream was added in 1.7.0
      return;
    } else if (version.isLessThan(new GitVersion(1, 8, 0))) {
      cmd.addParameters("branch", "--set-upstream", myLocalBranch, myUpstreamBranch);
    } else {
      cmd.addParameters("branch", "--set-upstream-to=" + myUpstreamBranch);
    }
    ExecResult r = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, r);
  }
}
