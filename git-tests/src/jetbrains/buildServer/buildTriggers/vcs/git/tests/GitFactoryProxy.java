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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import org.jetbrains.annotations.NotNull;

public class GitFactoryProxy implements GitFactory {
  private final GitAgentSSHService mySshService;
  private final AgentPluginConfig myConfig;
  private final File myTempDir;
  private final Map<String, String> myEnv;
  private final Map<String, List<String>> myInvokedMethods;
  private final Map<String, GitCommandProxyCallback> myCallbacks;
  private final Context myCtx;

  public GitFactoryProxy(@NotNull GitAgentSSHService sshService,
                         @NotNull AgentPluginConfig config,
                         @NotNull File tempDir,
                         @NotNull Map<String, String> env,
                         @NotNull Map<String, List<String>> invokedMethods,
                         @NotNull Map<String, GitCommandProxyCallback> callbacks,
                         @NotNull Context ctx) {
    mySshService = sshService;
    myConfig = config;
    myTempDir = tempDir;
    myInvokedMethods = invokedMethods;
    myEnv = env;
    myCallbacks = callbacks;
    myCtx = ctx;
  }

  @NotNull
  public GitFacade create(@NotNull File repositoryDir) {
    GitFacade facade = new NativeGitFacade(mySshService, myConfig.getPathToGit(), myConfig.getGitVersion(), repositoryDir, myTempDir,
                                           myConfig.isDeleteTempFiles(), GitProgressLogger.NO_OP, myConfig.getGitExec(), myEnv,
                                           myConfig.getCustomConfig(), myCtx);
    return (GitFacade)Proxy.newProxyInstance(GitFacadeProxy.class.getClassLoader(), new Class[]{GitFacade.class},
                                             new GitFacadeProxy(facade, myInvokedMethods, myCallbacks));
  }
}
