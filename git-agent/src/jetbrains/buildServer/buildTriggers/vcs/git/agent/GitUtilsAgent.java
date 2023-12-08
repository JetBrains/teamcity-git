/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import java.util.List;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.ExtraHTTPCredentials;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.PropertiesHelper;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.GIT_HTTP_CRED_PREFIX;

/**
 * Some utility methods which used JGit library classes.
 * Several methods contains in the server GitServerUtil.
 * The methods are not in the common module to make the module do not depends on JGit library.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class GitUtilsAgent {

  public static String getShortBranchName(@NotNull String fullRefName) {
    if (isRegularBranch(fullRefName))
      return fullRefName.substring(Constants.R_HEADS.length());
    return fullRefName;
  }

  public static boolean isRegularBranch(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_HEADS);
  }

  public static boolean isTag(@NotNull Ref ref) {
    return isTag(ref.getName());
  }

  public static boolean isTag(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_TAGS);
  }

  public static boolean isAnonymousGitWithUsername(@NotNull URIish uri) {
    return "git".equals(uri.getScheme()) && uri.getUser() != null;
  }

  public static List<ExtraHTTPCredentials> detectExtraHTTPCredentialsInBuild(@NotNull AgentRunningBuild build) {
    return GitUtils.processExtraHTTPCredentials(PropertiesHelper.aggregatePropertiesByAlias(build.getSharedConfigParameters(), GIT_HTTP_CRED_PREFIX));
  }
}
