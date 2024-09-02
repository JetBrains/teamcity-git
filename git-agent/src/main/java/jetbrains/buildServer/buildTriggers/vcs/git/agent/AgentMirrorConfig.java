

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorConfig;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class AgentMirrorConfig implements MirrorConfig {

  private final BuildAgentConfiguration myAgentConfig;

  public AgentMirrorConfig(@NotNull BuildAgentConfiguration agentConfig) {
    myAgentConfig = agentConfig;
  }

  @NotNull
  public File getCachesDir() {
    return myAgentConfig.getCacheDirectory("git");
  }
}