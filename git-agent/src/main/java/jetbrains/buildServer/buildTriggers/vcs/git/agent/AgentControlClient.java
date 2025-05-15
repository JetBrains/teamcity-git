package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NotNull;

public interface AgentControlClient {

  public void terminateAgent(@NotNull String reason);

  public void disableAgent(@NotNull String reason);

}
