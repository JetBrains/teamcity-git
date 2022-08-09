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

import java.util.*;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.LsRemoteCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil.splitByLines;

public class LsRemoteCommandImpl extends BaseAuthCommandImpl<LsRemoteCommand> implements LsRemoteCommand {

  private boolean myPeelRefs = false;
  private boolean myTags = false;

  private final List<String> myLsRemoteBranches = new ArrayList<>();

  public LsRemoteCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public LsRemoteCommand peelRefs() {
    myPeelRefs = true;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setTags() {
    myTags = true;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setBranches(String... lsRemoteBranches) {
    myLsRemoteBranches.addAll(Arrays.asList(lsRemoteBranches));
    return this;
  }

  @NotNull
  public List<Ref> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("ls-remote");
    if (myTags) {
      cmd.addParameter("--tags");
    }
    cmd.addParameter("origin");

    if (!myLsRemoteBranches.isEmpty()) {
      cmd.addParameters(myLsRemoteBranches);
    }

    return parse(runCmd(cmd.stdErrLogLevel("debug")).getStdout());
  }

  private List<Ref> parse(@NotNull final String str) throws VcsException {
    final Map<String, Ref> refs = new HashMap<>();
    for (String line : splitByLines(str)) {

      final String objectId = line.substring(0, 40);
      String name = line.substring(40).trim();

      if (myPeelRefs && name.endsWith("^{}")) {
        name = name.substring(0, name.length() - 3);
        final Ref prior = refs.get(name);
        if (prior == null) throw new VcsException(String.format("Advertisement of %s^'{}' came before %s", name, name));
      }

      refs.put(name, new RefImpl(name, objectId));
    }
    return new ArrayList<>(refs.values());
  }
}
