/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.BranchInfo;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * The "git branch" command
 */
public class BranchCommand extends RepositoryCommand {
  /**
   * The branch command
   *
   * @param settings the command settings
   */
  public BranchCommand(@NotNull final AgentSettings settings) {
    super(settings);
  }

  /**
   * Get local branch information
   *
   * @param branchName the branch name (short variant)
   * @return the information about that branch
   * @throws VcsException if there is problem with running git
   */
  public BranchInfo branchInfo(String branchName) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("branch");
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
    for (String l : r.getStdout().split("\n")) {
      if (l.length() < 2) {
        continue;
      }
      String b = l.substring(2).trim();
      if (b.equals(branchName)) {
        return new BranchInfo(true, l.charAt(0) == '*');
      }
    }
    return new BranchInfo(false, false);

  }

  /**
   * Reset non-current branch to the specified revision
   *
   * @param branch   the branch name (short)
   * @param revision the revision to reset to
   * @throws VcsException if there is a problem with running git
   */
  public void setBranchCommit(String branch, String revision) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("update-ref", "-m", "setting revision to checkout", GitUtils.expandRef(branch), revision);
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }

  /**
   * Force checkout the current branch
   *
   * @param branch the branch name (short)
   * @throws VcsException if there is a problem with running git
   */
  public void forceCheckout(String branch) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("checkout", "-q", "-f", branch);
    runCommand(cmd);
  }


  /**
   * Create branch tracking other branch
   *
   * @param branch  the branch to create
   * @param tracked the tracked branch
   * @throws VcsException if there is a problem with running git
   */
  public void createBranch(String branch, String tracked) throws VcsException {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameters("branch", "-l", "--track", branch, tracked);
    ExecResult r = runCommand(cmd);
    failIfNotEmptyStdErr(cmd, r);
  }
}
