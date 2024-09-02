

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.vcs.VcsException;

/**
 * @author dmitry.neverov
 */
public class GitPathResolverImpl implements GitPathResolver {

  public GitPathResolverImpl() {
  }

  public String resolveGitPath(final BuildAgentConfiguration agentConfiguration, String pathToResolve) throws VcsException {
    ValueResolver resolver = agentConfiguration.getParametersResolver();
    ProcessingResult result = resolver.resolve(pathToResolve);
    if (!result.isFullyResolved()) {
      throw new VcsException("The value is not fully resolved: " + result.getResult());
    }
    return result.getResult();
  }
}