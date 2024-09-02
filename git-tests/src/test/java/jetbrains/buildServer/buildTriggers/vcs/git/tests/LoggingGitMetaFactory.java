

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitMetaFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import org.jetbrains.annotations.NotNull;

public class LoggingGitMetaFactory implements GitMetaFactory {
  private final Map<String, List<String>> myInvokedMethods = new HashMap<String, List<String>>();
  private final Map<String, GitCommandProxyCallback> myCallbacks = new HashMap<String, GitCommandProxyCallback>();

  @NotNull
  public GitFactory createFactory(@NotNull GitAgentSSHService sshService,
                                  @NotNull Context ctx) {
    return new GitFactoryProxy(sshService, myInvokedMethods, myCallbacks, ctx);
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
      if ("call".equals(method) || "callWithIgnoreExitCode".equals(method)) {
        result++;
      }
    }
    return result;
  }

  public void addCallback(@NotNull String gitFacadeMethodName, @NotNull GitCommandProxyCallback callback) {
    myCallbacks.put(gitFacadeMethodName, callback);
  }
}