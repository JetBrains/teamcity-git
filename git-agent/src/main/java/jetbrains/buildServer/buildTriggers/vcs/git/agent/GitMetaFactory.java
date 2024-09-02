

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import org.jetbrains.annotations.NotNull;

public interface GitMetaFactory {

  @NotNull
  GitFactory createFactory(@NotNull GitAgentSSHService sshService,
                           @NotNull Context ctx);

}