

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.vcs.VcsException;

/**
 * Resolves path to git executable
 * @author dmitry.neverov
 */
public interface GitPathResolver {

  public String resolveGitPath(final BuildAgentConfiguration agentConfiguration, String pathToResolve) throws VcsException;

}