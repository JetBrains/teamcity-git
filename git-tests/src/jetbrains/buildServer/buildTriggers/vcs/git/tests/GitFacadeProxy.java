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

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitFacadeProxy implements InvocationHandler {
  private final Object myGitFacade;
  private final Map<String, List<String>> myInvokedMethods;

  public GitFacadeProxy(@NotNull Object gitFacade,
                        @NotNull Map<String, List<String>> invokedMethods) {
    myGitFacade = gitFacade;
    myInvokedMethods = invokedMethods;
  }

  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Object command = method.invoke(myGitFacade, args);
    Class<?> gitCommandClass = method.getReturnType();
    List<String> methods = myInvokedMethods.get(gitCommandClass.getName());
    if (methods == null) {
      methods = new ArrayList<String>();
      myInvokedMethods.put(gitCommandClass.getName(), methods);
    }
    return Proxy.newProxyInstance(GitFacadeProxy.class.getClassLoader(), new Class[]{gitCommandClass},
                                  new GitCommandProxy(command, gitCommandClass, methods));
  }
}
