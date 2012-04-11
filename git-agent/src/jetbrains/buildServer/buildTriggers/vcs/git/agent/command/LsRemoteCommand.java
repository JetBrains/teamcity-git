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
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.splitByLines;

/**
 * @author dmitry.neverov
 */
public class LsRemoteCommand extends RepositoryCommand {

  private String myRemoteName = "origin";
  private boolean myShowTags = false;
  private final GitAgentSSHService mySsh;

  public LsRemoteCommand(AgentSettings mySettings, String workDirectory, GitAgentSSHService ssh) {
    super(mySettings, workDirectory);
    mySsh = ssh;
  }

  public void showTags() {
    myShowTags = true;
  }

  @NotNull
  public List<Ref> execute() {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("ls-remote");
    if (myShowTags)
      cmd.addParameter("--tags");
    cmd.addParameter(myRemoteName);
    try {
      ExecResult result;
      if (mySettings.isUseNativeSSH()) {
        result = runCommand(cmd);
      } else {
        SshHandler h = new SshHandler(mySsh, cmd);
        try {
          result = runCommand(cmd);
        } finally {
          h.unregister();
        }
      }
      return parse(result.getStdout());
    } catch (VcsException e) {
      return Collections.emptyList();
    }
  }

  private List<Ref> parse(@NotNull final String str) {
    List<Ref> refs = new ArrayList<Ref>();
    for (String line : splitByLines(str)) {
      if (isEmpty(line))
        continue;
      String objectId = line.substring(0, 40);
      String name = line.substring(40, line.length()).trim();
      refs.add(new RefImpl(name, objectId));
    }
    return refs;
  }
}
