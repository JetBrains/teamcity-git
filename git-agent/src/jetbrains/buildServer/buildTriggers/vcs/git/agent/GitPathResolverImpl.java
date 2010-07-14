/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.parameters.AgentParameterResolverFactory;
import jetbrains.buildServer.parameters.CompositeParametersProvider;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.parameters.impl.CompositeParametersProviderImpl;
import jetbrains.buildServer.parameters.impl.DynamicContextVariables;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class GitPathResolverImpl implements GitPathResolver {

  @NotNull private final AgentParameterResolverFactory myResolverFactory;

  public GitPathResolverImpl(@NotNull final AgentParameterResolverFactory resolverFactory) {
    myResolverFactory = resolverFactory;
  }

  public String resolveGitPath(final BuildAgentConfiguration agentConfiguration, String pathToResolve) throws VcsException {
    ValueResolver valueResolver = getValueResolver(agentConfiguration);
    ProcessingResult result = valueResolver.resolve(pathToResolve);
    if (!result.isFullyResolved()) {
      throw new VcsException("The value is not fully resolved: " + result.getResult());
    }
    return result.getResult();
  }


  /**
   * Create a value resolver
   *
   * @param agentConfiguration the build agent configuration
   * @return a created value resolver
   */
  private ValueResolver getValueResolver(final BuildAgentConfiguration agentConfiguration) {
    final CompositeParametersProvider parametersProvider = new CompositeParametersProviderImpl();
    parametersProvider
      .appendParametersProvider(new MapParametersProviderImpl("agent parameters", agentConfiguration.getAgentParameters()));
    parametersProvider
      .appendParametersProvider(new MapParametersProviderImpl("agent custom parameters", agentConfiguration.getCustomProperties()));

    final DynamicContextVariables variables = new DynamicContextVariables();

    return myResolverFactory.createValueResolver(variables, parametersProvider);
  }
}
