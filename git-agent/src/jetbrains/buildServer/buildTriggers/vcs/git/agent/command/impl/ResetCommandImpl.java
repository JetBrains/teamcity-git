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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ResetCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class ResetCommandImpl implements ResetCommand {

  private final GitCommandLine myCmd;
  private boolean myHard = false;
  private String myRevision;

  public ResetCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public ResetCommand setHard(boolean doHard) {
    myHard = doHard;
    return this;
  }

  @NotNull
  public ResetCommand setRevision(@NotNull String revision) {
    myRevision = revision;
    return this;
  }

  public void call() throws VcsException {
    myCmd.addParameters("reset");
    if (myHard)
      myCmd.addParameter("--hard");
    myCmd.addParameter(myRevision);
    try {
      CommandUtil.runCommand(myCmd);
    } catch (VcsException e) {
      String message = e.getMessage();
      if (message != null && message.contains("fatal: index file smaller than expected")) {
        File workingDir = myCmd.getWorkingDirectory();
        File gitIndex = new File(new File(workingDir, ".git"), "index");
        throw new GitIndexCorruptedException(gitIndex, e);
      }
      throw e;
    }
  }
}
