/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefBatchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.util.FastByteArrayBuilder;

import static org.apache.commons.codec.Charsets.UTF_8;


public class UpdateRefBatchCommandImpl extends BaseCommandImpl implements UpdateRefBatchCommand {
  private FastByteArrayBuilder myInput = new FastByteArrayBuilder();

  public UpdateRefBatchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand update(@NotNull final String ref, @NotNull final String value, @Nullable final String oldValue) {
    //update SP <ref> NUL <newValue> NUL [<oldValue>] NUL
    cmd("update");
    arg(ref);
    arg(value);
    arg(oldValue);
    return this;
  }


  @NotNull
  @Override
  public UpdateRefBatchCommand create(@NotNull final String ref, @NotNull final String value) {
    //create SP <ref> NUL <newValue> NUL
    cmd("create");
    arg(ref);
    arg(value);
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand delete(@NotNull final String ref, @Nullable final String oldValue) {
    //delete SP <ref> NUL [<oldValue>] NUL
    cmd("delete");
    arg(ref);
    arg(oldValue);
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand verify(@NotNull final String ref, @Nullable final String oldValue) {
    //verify SP <ref> NUL [<oldValue>] NUL
    cmd("verify");
    arg(ref);
    arg(oldValue);
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand option(@NotNull final String option) {
    //option SP <opt> NUL
    cmd("option");
    arg(option);
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("update-ref");
    cmd.addParameter("--stdin");
    cmd.addParameter("-z");
    byte[] input = myInput.toByteArray();
    ExecResult r = CommandUtil.runCommand(cmd, input);
    CommandUtil.failIfNotEmptyStdErr(cmd, r);
  }

  private void cmd(String cmd) {
    myInput.append(cmd.getBytes(UTF_8));
    myInput.append((byte)0x20); // SP
  }

  private void arg(@Nullable final String arg) {
    if (arg != null) {
      myInput.append(arg.getBytes(UTF_8));
    }
    myInput.append((byte)0x0); // NUL
  }
}
