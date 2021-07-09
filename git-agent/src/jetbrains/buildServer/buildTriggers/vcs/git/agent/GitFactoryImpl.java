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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class GitFactoryImpl implements GitFactory {

  private final GitAgentSSHService mySsh;
  private final Context myCtx;

  public GitFactoryImpl(@NotNull GitAgentSSHService ssh,
                        @NotNull Context ctx) {
    mySsh = ssh;
    myCtx = ctx;
  }


  @NotNull
  public AgentGitFacade create(@NotNull File repositoryDir) {
    NativeGitFacade git = new NativeGitFacade(mySsh, repositoryDir, myCtx);
    git.setSshKeyManager(mySsh.getSshKeyManager());
    return git;
  }
}
