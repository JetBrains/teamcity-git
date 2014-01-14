/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanFilesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CleanCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class CleanCommandImpl implements CleanCommand {

  private final GitCommandLine myCmd;
  private AgentCleanFilesPolicy myCleanPolicy = AgentCleanFilesPolicy.ALL_UNTRACKED;

  public CleanCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public CleanCommand setCleanPolicy(@NotNull AgentCleanFilesPolicy policy) {
    myCleanPolicy = policy;
    return this;
  }

  public void call() throws VcsException {
    myCmd.addParameters("clean", "-f", "-d");
    switch (myCleanPolicy) {
      case ALL_UNTRACKED:
        myCmd.addParameter("-x");
        break;
      case IGNORED_ONLY:
        myCmd.addParameter("-X");
        break;
      case NON_IGNORED_ONLY:
        break;
    }
    ExecResult r = CommandUtil.runCommand(myCmd);
    CommandUtil.failIfNotEmptyStdErr(myCmd, r);
  }
}
