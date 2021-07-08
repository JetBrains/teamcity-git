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

import java.io.File;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CheckoutCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.Errors;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitOutdatedIndexException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings.with;

public class CheckoutCommandImpl extends BaseCommandImpl implements CheckoutCommand {

  private boolean myForce;
  private String myBranch;
  private Integer myTimeout;
  private boolean myUseNativeSsh;
  private AuthSettings myAuthSettings;

  public CheckoutCommandImpl(@NotNull AgentGitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public CheckoutCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  public CheckoutCommand setBranch(@NotNull String branch) {
    myBranch = branch;
    return this;
  }

  @NotNull
  public CheckoutCommand setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  @NotNull
  @Override
  public CheckoutCommand setAuthSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return this;
  }

  @NotNull
  @Override
  public CheckoutCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  public void call() throws VcsException {
    AgentGitCommandLine cmd = getCmd();
    cmd.addParameters("checkout", "-q");
    if (myForce)
      cmd.addParameter("-f");
    cmd.addParameter(myBranch);
    int timeout = myTimeout != null ? myTimeout : CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
    try {
      cmd.run(with().timeout(timeout)
                .authSettings(myAuthSettings)
                .useNativeSsh(myUseNativeSsh));
    } catch (VcsException e) {
      if (Errors.isCorruptedIndexError(e)) {
        File workingDir = cmd.getWorkingDirectory();
        File gitIndex = new File(new File(workingDir, ".git"), "index");
        throw new GitIndexCorruptedException(gitIndex, e);
      }
      if (Errors.isOutdatedIndexError(e)) {
        throw new GitOutdatedIndexException(e);
      }
      throw e;
    }
  }
}
