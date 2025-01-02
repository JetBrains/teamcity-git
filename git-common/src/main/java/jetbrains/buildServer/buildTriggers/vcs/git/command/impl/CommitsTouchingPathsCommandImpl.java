/*
 * Copyright 2000-2025 JetBrains s.r.o.
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

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.CommitsTouchingPathsCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommitsTouchingPathsCommandImpl extends BaseCommandImpl implements CommitsTouchingPathsCommand {
  private String myStartCommit;
  private Collection<String> myExcludedCommits;
  private int myMaxCommits = 1;

  public CommitsTouchingPathsCommandImpl(@NotNull final GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public CommitsTouchingPathsCommand setStartCommit(@NotNull final String commit) {
    myStartCommit = commit;
    return this;
  }

  @NotNull
  @Override
  public CommitsTouchingPathsCommand setExcludedCommits(@NotNull final Collection<String> excludedCommits) {
    myExcludedCommits = new HashSet<>(excludedCommits);
    return this;
  }

  @NotNull
  @Override
  public CommitsTouchingPathsCommand setMaxCommits(final int maxCommits) {
    myMaxCommits = maxCommits;
    return this;
  }

  @NotNull
  @Override
  public List<String> call(@NotNull final Collection<String> paths) throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("log");
    if (myStartCommit != null) {
      cmd.addParameter(myStartCommit);
    }
    for (String excludedCommit : myExcludedCommits) {
      cmd.addParameter("^" + excludedCommit);
    }

    cmd.addParameter("-" + myMaxCommits);

    cmd.addParameter("--");
    for (String path : paths) {
      cmd.addParameter(path);
    }

    ExecResult r = CommandUtil.runCommand(cmd);
    String stdout = r.getStdout().trim();
    return StringUtil.isEmpty(stdout) ? Collections.emptyList() : Arrays.asList(StringUtil.splitByLines(stdout));
  }
}
