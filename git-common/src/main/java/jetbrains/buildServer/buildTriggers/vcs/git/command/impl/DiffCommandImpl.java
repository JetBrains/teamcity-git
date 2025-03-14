

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.DiffCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class DiffCommandImpl extends BaseCommandImpl implements DiffCommand {

  private String myStartCommit;
  private Collection<String> myExcludedCommits;
  private String myFormat;

  public DiffCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public DiffCommand setStartCommit(@NotNull final String startCommit) {
    myStartCommit = startCommit;
    return this;
  }

  @NotNull
  @Override
  public DiffCommand setExcludedCommits(@NotNull final Collection<String> excludedCommits) {
    myExcludedCommits = excludedCommits;
    return this;
  }

  @NotNull
  @Override
  public DiffCommand setFormat(@NotNull final String format) {
    myFormat = format;
    return this;
  }

  @NotNull
  @Override
  public List<String> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("diff");
    if (myFormat != null) {
      cmd.addParameter(myFormat);
    }
    if (myStartCommit != null) {
      cmd.addParameter(myStartCommit);
    }
    for (String excludedCommit : myExcludedCommits) {
      cmd.addParameter("^" + excludedCommit);
    }

    ExecResult r = CommandUtil.runCommand(cmd);
    String stdout = r.getStdout().trim();
    return StringUtil.isEmpty(stdout) ? Collections.emptyList() : Arrays.asList(StringUtil.splitByLines(stdout));
  }
}