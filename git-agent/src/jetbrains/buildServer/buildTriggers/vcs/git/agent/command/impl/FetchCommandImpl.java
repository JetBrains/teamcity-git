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

import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.Errors;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings.with;

public class FetchCommandImpl extends BaseCommandImpl implements FetchCommand {

  private boolean myUseNativeSsh;
  private int myTimeout;
  private String myRefspec;
  private boolean myQuite;
  private boolean myShowProgress;
  private AuthSettings myAuthSettings;
  private Integer myDepth;
  private boolean myFetchTags = true;

  public FetchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public FetchCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  @NotNull
  public FetchCommand setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
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
  public FetchCommand setAuthSettings(@NotNull AuthSettings settings) {
    myAuthSettings = settings;
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
    GitCommandLine cmd = getCmd();
    cmd.addParameter("fetch");
    if (myQuite)
      cmd.addParameter("-q");
    if (myShowProgress)
      cmd.addParameter("--progress");
    if (myDepth != null)
      cmd.addParameter("--depth=" + myDepth);
    if (!myFetchTags)
      cmd.addParameter("--no-tags");
    cmd.addParameter("origin");
    cmd.addParameter(myRefspec);
    cmd.setHasProgress(true);
    try {
      cmd.run(with().timeout(myTimeout)
                  .authSettings(myAuthSettings)
                  .useNativeSsh(myUseNativeSsh));
    } catch (VcsException e) {
      if (Errors.isCorruptedIndexError(e)) {
        File workingDir = cmd.getWorkingDirectory();
        File gitIndex = new File(new File(workingDir, ".git"), "index");
        throw new GitIndexCorruptedException(gitIndex, e);
      }
      if (CommandUtil.isTimeoutError(e)) {
        throw new GitExecTimeout();
      }
      throw e;
    }
  }
}
