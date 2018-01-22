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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class UpdateRefCommandImpl extends BaseCommandImpl implements UpdateRefCommand {

  private String myRef;
  private String myRevision;
  private boolean myDelete;

  public UpdateRefCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public UpdateRefCommand setRef(@NotNull String ref) {
    myRef = ref;
    return this;
  }

  @NotNull
  public UpdateRefCommand setRevision(@NotNull String revision) {
    myRevision = revision;
    return this;
  }

  @NotNull
  public UpdateRefCommand delete() {
    myDelete = true;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("update-ref");
    if (myDelete)
      cmd.addParameter("-d");
    cmd.addParameter(myRef);
    if (myRevision != null)
      cmd.addParameter(myRevision);
    ExecResult r = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, r);
  }
}
