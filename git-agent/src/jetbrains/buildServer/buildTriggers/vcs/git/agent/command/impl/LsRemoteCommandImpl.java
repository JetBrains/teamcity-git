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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.Retry;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LsRemoteCommand;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.splitByLines;
import static jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings.with;

public class LsRemoteCommandImpl extends BaseCommandImpl implements LsRemoteCommand {

  private boolean myShowTags = false;
  private AuthSettings myAuthSettings;
  private boolean myUseNativeSsh = false;
  private int myRetryAttempts = 1;
  private int myTimeoutSeconds;

  public LsRemoteCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public LsRemoteCommand showTags() {
    myShowTags = true;
    return this;
  }

  @NotNull
  public LsRemoteCommand setAuthSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return this;
  }

  @NotNull
  public LsRemoteCommand setUseNativeSsh(boolean useNativeSsh) {
    myUseNativeSsh = useNativeSsh;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setTimeout(int timeoutSeconds) {
    myTimeoutSeconds = timeoutSeconds;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setRetryAttempts(int num) {
    myRetryAttempts = num;
    return this;
  }

  @NotNull
  public List<Ref> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("ls-remote");
    if (myShowTags)
      cmd.addParameter("--tags");
    cmd.addParameter("origin");

    try {
      return Retry.retry(new Retry.Retryable<List<Ref>>() {
        @Override
        public boolean requiresRetry(@NotNull final Exception e) {
          return CommandUtil.isRecoverable(e);
        }

        @Override
        public List<Ref> call() throws VcsException {
          final ExecResult result = cmd.run(with()
                                              .timeout(myTimeoutSeconds)
                                              .authSettings(myAuthSettings)
                                              .useNativeSsh(myUseNativeSsh));
          return parse(result.getStdout());
        }

        @NotNull
        @Override
        public Logger getLogger() {
          return Loggers.VCS;
        }
      }, myRetryAttempts);
    } catch (Exception t) {
      if (t instanceof VcsException) throw (VcsException)t;
      throw new VcsException(t);
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
