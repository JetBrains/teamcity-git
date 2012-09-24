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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.AskPassGenerator;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.SshHandler;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitCommandLine extends GeneralCommandLine {

  private final GitAgentSSHService mySsh;
  private final AskPassGenerator myAskPassGen;
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();
  private File myWorkingDirectory;

  public GitCommandLine(@Nullable GitAgentSSHService ssh,
                        @NotNull AskPassGenerator askPassGen) {
    mySsh = ssh;
    myAskPassGen = askPassGen;
  }

  public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
    GitVcsRoot.AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings != null) {
      if (mySsh == null)
        throw new IllegalStateException("Ssh is not initialized");
      if (authSettings.getAuthMethod() == AuthenticationMethod.PASSWORD) {
        try {
          final File askPass = myAskPassGen.generate(authSettings);
          getParametersList().addAt(0, "-c");
          getParametersList().addAt(1, "core.askpass=" + askPass.getAbsolutePath());
          addPostAction(new Runnable() {
            public void run() {
              FileUtil.delete(askPass);
            }
          });
        } catch (IOException e) {
          throw new VcsException(e);
        }
      }
      if (settings.isUseNativeSsh()) {
        return CommandUtil.runCommand(this, settings.getTimeout());
      } else {
        SshHandler h = new SshHandler(mySsh, authSettings, this);
        try {
          return CommandUtil.runCommand(this, settings.getTimeout());
        } finally {
          h.unregister();
        }
      }
    } else {
      return CommandUtil.runCommand(this, settings.getTimeout());
    }
  }

  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }

  public List<Runnable> getPostActions() {
    return myPostActions;
  }

  @Override
  public void setWorkingDirectory(File workingDirectory) {
    myWorkingDirectory = workingDirectory;
    super.setWorkingDirectory(workingDirectory);
  }

  @Nullable
  public File getWorkingDirectory() {
    return myWorkingDirectory;
  }
}
