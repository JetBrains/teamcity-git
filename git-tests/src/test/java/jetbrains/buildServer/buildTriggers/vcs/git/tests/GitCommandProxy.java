

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GitCommandProxy implements InvocationHandler {
  private final Object myGitCommand;
  private final Class myGitCommandClass;
  private final List<String> myInvokedMethods;
  private final Map<String, GitCommandProxyCallback> myCallbacks;
  public GitCommandProxy(@NotNull Object gitCommand,
                         @NotNull Class gitCommandClass,
                         @NotNull List<String> invokedMethods,
                         @NotNull Map<String, GitCommandProxyCallback> callbacks) {
    myGitCommand = gitCommand;
    myGitCommandClass = gitCommandClass;
    myInvokedMethods = invokedMethods;
    myCallbacks = callbacks;
  }

  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    myInvokedMethods.add(method.getName());
    GitCommandProxyCallback callback = myCallbacks.get(myGitCommandClass.getName() + "." + method.getName());
    if (callback != null) {
      final Optional<Object> call = callback.call(method, args);
      if (call != null) {
        return call.isPresent() ? call.get() : null;
      }
    }
    Object result = method.invoke(myGitCommand, args);
    if (myGitCommandClass.isInstance(result)) {//case of chaining
      return Proxy.newProxyInstance(GitFacadeProxy.class.getClassLoader(),
                                    new Class[]{myGitCommandClass},
                                    new GitCommandProxy(myGitCommand, myGitCommandClass, myInvokedMethods, myCallbacks));
    }
    return result;
  }
}