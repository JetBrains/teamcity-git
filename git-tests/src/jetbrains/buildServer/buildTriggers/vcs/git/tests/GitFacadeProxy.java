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
  private final Map<String, GitCommandProxyCallback> myCallbacks;

  public GitFacadeProxy(@NotNull Object gitFacade,
                        @NotNull Map<String, List<String>> invokedMethods,
                        @NotNull Map<String, GitCommandProxyCallback> callbacks) {
    myGitFacade = gitFacade;
    myInvokedMethods = invokedMethods;
    myCallbacks = callbacks;
  }

  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Object result = method.invoke(myGitFacade, args);
    Class<?> resultClass = method.getReturnType();
    if (!resultClass.isInterface()) {//all git commands are interfaces, if result is not return it as is
      return result;
    }
    List<String> methods = myInvokedMethods.get(resultClass.getName());
    if (methods == null) {
      methods = new ArrayList<String>();
      myInvokedMethods.put(resultClass.getName(), methods);
    }
    return Proxy.newProxyInstance(GitFacadeProxy.class.getClassLoader(), new Class[]{resultClass},
                                  new GitCommandProxy(result, resultClass, methods, myCallbacks));
  }
}
