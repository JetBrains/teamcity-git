

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
    AgentGitFacadeImpl git = new AgentGitFacadeImpl(mySsh, repositoryDir, myCtx);
    git.setSshKeyManager(mySsh.getSshKeyManager());
    return git;
  }
}