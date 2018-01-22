/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
