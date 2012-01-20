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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class DeleteTagCommand extends RepositoryCommand {

  private String myTagName;

  public DeleteTagCommand(AgentSettings mySettings) {
    super(mySettings);
  }

  public DeleteTagCommand(AgentSettings mySettings, String workDirectory) {
    super(mySettings, workDirectory);
  }

  public void setTagName(@NotNull String tagName) {
    myTagName = tagName;
  }

  public void execute() throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("tag");
    cmd.addParameter("-d");
    cmd.addParameter(myTagName);
    runCommand(cmd);
  }

}
