

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