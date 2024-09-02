

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

/**
 * @author dmitry.neverov
 */
public interface PluginConfigFactory {

  AgentPluginConfig createConfig(AgentRunningBuild build, VcsRoot root) throws VcsException;

}