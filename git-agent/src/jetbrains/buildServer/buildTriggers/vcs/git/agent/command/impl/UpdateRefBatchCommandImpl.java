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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefBatchCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.FastByteArrayOutputStream;

import java.io.IOException;

import static org.apache.commons.codec.Charsets.UTF_8;


public class UpdateRefBatchCommandImpl extends BaseCommandImpl implements UpdateRefBatchCommand {
  private FastByteArrayOutputStream myInput = new FastByteArrayOutputStream();

  public UpdateRefBatchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand update(@NotNull final String ref, @NotNull final String value, @Nullable final String oldValue)
    throws VcsException {
    //update SP <ref> NUL <newValue> NUL [<oldValue>] NUL
    try {
      myInput.write("update".getBytes(UTF_8));
      myInput.write(0x20);
      myInput.write(ref.getBytes(UTF_8));
      myInput.write(0x0);
      myInput.write(value.getBytes(UTF_8));
      myInput.write(0x0);
      if (oldValue != null) {
        myInput.write(oldValue.getBytes(UTF_8));
      }
      myInput.write(0x0);
    } catch (IOException ignored) {
      throw new VcsException("Failed to generate update-ref command binary input");
    }
    return this;
  }


  @NotNull
  @Override
  public UpdateRefBatchCommand create(@NotNull final String ref, @NotNull final String value) throws VcsException {
    //create SP <ref> NUL <newValue> NUL
    try {
      myInput.write("create".getBytes(UTF_8));
      myInput.write(0x20);
      myInput.write(ref.getBytes(UTF_8));
      myInput.write(0x0);
    } catch (IOException ignored) {
      throw new VcsException("Failed to generate update-ref command binary input");
    }
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand delete(@NotNull final String ref, @Nullable final String oldValue) throws VcsException {
    //delete SP <ref> NUL [<oldValue>] NUL
    try {
      myInput.write("delete".getBytes(UTF_8));
      myInput.write(0x20);
      myInput.write(ref.getBytes(UTF_8));
      myInput.write(0x0);
      if (oldValue != null) {
        myInput.write(oldValue.getBytes(UTF_8));
      }
      myInput.write(0x0);
    } catch (IOException ignored) {
      throw new VcsException("Failed to generate update-ref command binary input");
    }
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand verify(@NotNull final String ref, @Nullable final String oldValue) throws VcsException {
    //verify SP <ref> NUL [<oldValue>] NUL
    try {
      myInput.write("verify".getBytes(UTF_8));
      myInput.write(0x20);
      myInput.write(ref.getBytes(UTF_8));
      myInput.write(0x0);
      if (oldValue != null) {
        myInput.write(oldValue.getBytes(UTF_8));
      }
      myInput.write(0x0);
    } catch (IOException ignored) {
      throw new VcsException("Failed to generate update-ref command binary input");
    }
    return this;
  }

  @NotNull
  @Override
  public UpdateRefBatchCommand option(@NotNull final String option) throws VcsException {
    //option SP <opt> NUL
    try {
      myInput.write("option".getBytes(UTF_8));
      myInput.write(0x20);
      myInput.write(option.getBytes(UTF_8));
      myInput.write(0x0);
    } catch (IOException ignored) {
      throw new VcsException("Failed to generate update-ref command binary input");
    }
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
}
