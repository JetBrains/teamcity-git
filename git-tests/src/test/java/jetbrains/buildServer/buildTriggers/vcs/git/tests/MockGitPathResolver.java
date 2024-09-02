

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitPathResolver;
import jetbrains.buildServer.vcs.VcsException;

public class MockGitPathResolver implements GitPathResolver {

  public String resolveGitPath(final BuildAgentConfiguration agentConfiguration, final String pathToResolve) throws VcsException {
    return pathToResolve;
  }
}