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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoggingGitMetaFactory implements GitMetaFactory {
  private final Map<String, List<String>> myInvokedMethods = new HashMap<String, List<String>>();
  private final Map<String, GitCommandProxyCallback> myCallbacks = new HashMap<String, GitCommandProxyCallback>();

  @NotNull
  public GitFactory createFactory(@NotNull GitAgentSSHService sshService,
                                  @NotNull AgentPluginConfig config,
                                  @NotNull GitProgressLogger logger,
                                  @NotNull File tempDir,
                                  @NotNull Map<String, String> env,
                                  @NotNull Context ctx) {
    return new GitFactoryProxy(sshService, config, tempDir, env, myInvokedMethods, myCallbacks, ctx);
  }


  public void clear() {
    myInvokedMethods.clear();
  }

  @NotNull
  public List<String> getInvokedMethods(@NotNull Class gitCommandClass) {
    List<String> methods = myInvokedMethods.get(gitCommandClass.getName());
    if (methods != null)
      return methods;
    return Collections.emptyList();
  }

  public int getNumberOfCalls(@NotNull Class gitCommandClass) {
    List<String> methods = myInvokedMethods.get(gitCommandClass.getName());
    if (methods == null)
      return 0;
    int result = 0;
    for (String method : methods) {
      if ("call".equals(method))
        result++;
    }
    return result;
  }

  public void addCallback(@NotNull String gitFacadeMethodName, @NotNull GitCommandProxyCallback callback) {
    myCallbacks.put(gitFacadeMethodName, callback);
  }
}
