/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dmitry.neverov
 */
public class ShowRefCommandImpl implements ShowRefCommand {

  private final GitCommandLine myCmd;
  private String myPattern;
  private boolean myShowTags;

  public ShowRefCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public ShowRefCommand setPattern(@NotNull String pattern) {
    myPattern = pattern;
    return this;
  }

  @NotNull
  public ShowRefCommand showTags() {
    myShowTags = true;
    return this;
  }


  @NotNull
  public Map<String, Ref> call() {
    myCmd.addParameter("show-ref");
    if (myPattern != null)
      myCmd.addParameters(myPattern);
    if (myShowTags)
      myCmd.addParameter("--tags");
    try {
      ExecResult result = CommandUtil.runCommand(myCmd);
      return parse(result.getStdout());
    } catch (VcsException e) {
      return Collections.emptyMap();
    }
  }

  private Map<String, Ref> parse(String str) {
    Map<String, Ref> result = new HashMap<String, Ref>();
    for (String line : StringUtil.splitByLines(str)) {
      if (line.length() < 41)
        continue;//a valid line of show-ref output contains 40 symbols of hash + space + branch name
      String commit = line.substring(0, 40);
      String ref = line.substring(41, line.length());
      result.put(ref, new RefImpl(ref, commit));
    }
    return result;
  }

}
