

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import org.jetbrains.annotations.NotNull;

public class GitMetaFactoryImpl implements GitMetaFactory {

  @NotNull
  public GitFactory createFactory(@NotNull GitAgentSSHService sshService,
                                  @NotNull Context ctx) {
    return new GitFactoryImpl(sshService, ctx);
  }
}