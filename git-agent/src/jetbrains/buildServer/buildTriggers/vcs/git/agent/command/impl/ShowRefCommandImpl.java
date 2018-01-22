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

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefResult;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ShowRefCommandImpl extends BaseCommandImpl implements ShowRefCommand {

  private final static String INVALID_REF_PREFIX = "error: ";
  private final static String INVALID_REF_SUFFIX = " does not point to a valid object!";
  private String myPattern;
  private boolean myShowTags;

  public ShowRefCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
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
  public ShowRefResult call() {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("show-ref");
    if (myPattern != null)
      cmd.addParameters(myPattern);
    if (myShowTags)
      cmd.addParameter("--tags");
    try {
      ExecResult result = CommandUtil.runCommand(cmd);
      return new ShowRefResult(parseValidRefs(result.getStdout()), parseInvalidRefs(result.getStderr()));
    } catch (VcsException e) {
      return new ShowRefResult(Collections.<String, Ref>emptyMap(), Collections.<String>emptySet());
    }
  }

  @NotNull
  private Map<String, Ref> parseValidRefs(String str) {
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

  @NotNull
  private Set<String> parseInvalidRefs(@NotNull String strerr) {
    if (strerr.isEmpty())
      return Collections.emptySet();
    Set<String> result = new HashSet<String>();
    for (String line : StringUtil.splitByLines(strerr)) {
      line = line.trim();
      if (line.startsWith(INVALID_REF_PREFIX) && line.endsWith(INVALID_REF_SUFFIX)) {
        String ref = line.substring(INVALID_REF_PREFIX.length(), line.length() - INVALID_REF_SUFFIX.length());
        result.add(ref);
      }
    }
    return result;
  }
}
