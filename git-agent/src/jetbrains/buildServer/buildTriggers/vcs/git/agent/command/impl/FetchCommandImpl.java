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

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class FetchCommandImpl extends BaseAuthCommandImpl<FetchCommand> implements FetchCommand {

  private String myRefspec;
  private boolean myQuite;
  private boolean myShowProgress;
  private Integer myDepth;
  private boolean myFetchTags = true;

  public FetchCommandImpl(@NotNull AgentGitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public FetchCommand setRefspec(@NotNull String refspec) {
    myRefspec = refspec;
    return this;
  }

  @NotNull
  public FetchCommand setQuite(boolean quite) {
    myQuite = quite;
    return this;
  }

  @NotNull
  public FetchCommand setShowProgress(boolean showProgress) {
    myShowProgress = showProgress;
    return this;
  }

  @NotNull
  public FetchCommand setDepth(int depth) {
    myDepth = depth;
    return this;
  }

  @NotNull
  public FetchCommand setFetchTags(boolean fetchTags) {
    myFetchTags = fetchTags;
    return this;
  }


  public void call() throws VcsException {
    AgentGitCommandLine cmd = getCmd();
    cmd.addParameter("fetch");
    if (myQuite)
      cmd.addParameter("-q");
    if (myShowProgress)
      cmd.addParameter("--progress");
    if (myDepth != null)
      cmd.addParameter("--depth=" + myDepth);
    if (!myFetchTags)
      cmd.addParameter("--no-tags");
    if (cmd.getGitVersion().isGreaterThan(new GitVersion(1, 7, 3))) {
      cmd.addParameter("--recurse-submodules=no"); // we process submodules separately
    }
    cmd.addParameter("origin");
    cmd.addParameter(myRefspec);
    cmd.setHasProgress(true);
    runCmd(cmd);
  }
}
