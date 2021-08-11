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

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class FetchCommandImpl extends BaseAuthCommandImpl<FetchCommand> implements FetchCommand {

  private final Set<String> myRefSpecs = new HashSet<>();
  private boolean myQuite;
  private boolean myShowProgress;
  private Integer myDepth;
  private boolean myFetchTags = true;
  private String myRemoteUrl;

  public FetchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public FetchCommand setRefspec(@NotNull String refspec) {
    myRefSpecs.add(refspec);
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

  @NotNull
  @Override
  public FetchCommand setRemote(@NotNull String remoteUrl) {
    myRemoteUrl = remoteUrl;
    return this;
  }

  public void call() throws VcsException {
    final GitCommandLine cmd = getCmd();
    final GitVersion gitVersion = cmd.getGitVersion();

    cmd.addParameter("fetch");
    if (myQuite)
      cmd.addParameter("-q");
    if (myShowProgress)
      cmd.addParameter("--progress");
    if (myDepth != null)
      cmd.addParameter("--depth=" + myDepth);
    if (!myFetchTags)
      cmd.addParameter("--no-tags");
    if (gitVersion.isGreaterThan(new GitVersion(1, 7, 3))) {
      cmd.addParameter("--recurse-submodules=no"); // we process submodules separately
    }

    cmd.setHasProgress(true);

    if (myRefSpecs.size() <= 1 || gitVersion.isLessThan(new GitVersion(2, 29, 0))) {
      cmd.addParameter(getRemote());
      myRefSpecs.forEach(refSpec -> cmd.addParameter(refSpec));
      runCmd(cmd);
    } else {
      cmd.addParameter("--stdin");
      cmd.addParameter(getRemote());
      runCmd(cmd.stdErrLogLevel("debug"), refSpecsToBytes(cmd));
    }
  }

  @NotNull
  private byte[] refSpecsToBytes(@NotNull GitCommandLine cmd) {
    final StringBuilder res = new StringBuilder();
    for (String refSpec : myRefSpecs) {
      res.append(refSpec).append("\n");
    }
    return res.toString().getBytes(cmd.getCharset());
  }

  @NotNull
  private String getRemote() {
    return myRemoteUrl == null ? "origin" : myRemoteUrl;
  }
}
