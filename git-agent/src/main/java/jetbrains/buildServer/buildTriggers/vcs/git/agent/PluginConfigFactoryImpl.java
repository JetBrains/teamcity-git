

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

/**
 * @author dmitry.neverov
 */
public final class PluginConfigFactoryImpl implements PluginConfigFactory {

  private final BuildAgentConfiguration myAgentConfig;
  private final GitDetector myGitDetector;

  public PluginConfigFactoryImpl(BuildAgentConfiguration agentConfig, GitDetector gitDetector) {
    myAgentConfig = agentConfig;
    myGitDetector = gitDetector;
  }


  public AgentPluginConfig createConfig(AgentRunningBuild build, VcsRoot root) throws VcsException {
    GitExec gitExec = myGitDetector.getGitPathAndVersion(root, myAgentConfig, build);
    return new PluginConfigImpl(myAgentConfig, build, root, gitExec);
  }

}