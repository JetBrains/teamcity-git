

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacade;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import org.jetbrains.annotations.NotNull;

public class GitFactoryProxy implements GitFactory {
  private final GitAgentSSHService mySshService;
  private final Map<String, List<String>> myInvokedMethods;
  private final Map<String, GitCommandProxyCallback> myCallbacks;
  private final Context myCtx;

  public GitFactoryProxy(@NotNull GitAgentSSHService sshService,
                         @NotNull Map<String, List<String>> invokedMethods,
                         @NotNull Map<String, GitCommandProxyCallback> callbacks,
                         @NotNull Context ctx) {
    mySshService = sshService;
    myInvokedMethods = invokedMethods;
    myCallbacks = callbacks;
    myCtx = ctx;
  }

  @NotNull
  public AgentGitFacade create(@NotNull File repositoryDir) {
    AgentGitFacade facade = new AgentGitFacadeImpl(mySshService, repositoryDir, myCtx);
    return (AgentGitFacade)Proxy.newProxyInstance(GitFacadeProxy.class.getClassLoader(), new Class[]{AgentGitFacade.class},
                                                  new GitFacadeProxy(facade, myInvokedMethods, myCallbacks));
  }
}