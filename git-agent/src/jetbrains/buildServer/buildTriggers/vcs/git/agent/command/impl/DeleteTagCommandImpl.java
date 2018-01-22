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

import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.DeleteTagCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class DeleteTagCommandImpl extends BaseCommandImpl implements DeleteTagCommand {

  private static final int TAG_PREFIX_LENGTH = "refs/tags/".length();
  private String myName;

  public DeleteTagCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public DeleteTagCommand setName(@NotNull String tagFullName) {
    if (tagFullName.length() < TAG_PREFIX_LENGTH)
      throw new IllegalArgumentException("Full tag name expected, " + tagFullName);
    myName = tagFullName.substring(TAG_PREFIX_LENGTH);
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("tag");
    cmd.addParameter("-d");
    cmd.addParameter(myName);
    CommandUtil.runCommand(cmd);
  }
}
