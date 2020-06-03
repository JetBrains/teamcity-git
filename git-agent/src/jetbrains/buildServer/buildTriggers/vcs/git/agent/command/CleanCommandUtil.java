/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanFilesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.UpdaterImpl;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CleanCommandUtil {

  @NotNull
  public static AgentCleanPolicy getCleanPolicy(@NotNull VcsRoot vcsRoot) {
    final String clean = vcsRoot.getProperty(Constants.AGENT_CLEAN_POLICY);
    return clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
  }

  @NotNull
  public static AgentCleanFilesPolicy getCleanFilesPolicy(@NotNull VcsRoot vcsRoot) {
    final String clean = vcsRoot.getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
    return clean == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(clean);
  }

  public static boolean isCleanCommandSupportsExclude(@NotNull GitVersion version) {
    return !version.isLessThan(UpdaterImpl.GIT_CLEAN_LEARNED_EXCLUDE);
  }


  public static boolean isCleanEnabled(@NotNull final VcsRoot vcsRoot) {
    return getCleanPolicy(vcsRoot) != AgentCleanPolicy.NEVER && getCleanFilesPolicy(vcsRoot) != AgentCleanFilesPolicy.IGNORED_ONLY;
  }

  public static boolean isClashingTargetPath(@NotNull String targetPath, @NotNull VcsRootEntry otherRoot, @NotNull GitVersion gitVersion) {
    final List<IncludeRule> includeRules = otherRoot.getCheckoutRules().getRootIncludeRules();
    if (includeRules.isEmpty()) return targetPath.isEmpty();

    final boolean cleanCommandSupportsExclude = isCleanCommandSupportsExclude(gitVersion);

    for (IncludeRule rule : includeRules) {
      if (targetPath.equals(rule.getTo())) return true;
      if (!cleanCommandSupportsExclude && targetPath.startsWith(rule.getTo())) return true;
    }
    return false;
  }

  @NotNull
  public static Collection<String> getSharedPaths(@NotNull VcsRootEntry otherRoot, @NotNull String targetPath) {
    final SortedSet<String> clashingPaths = new TreeSet<>();

    final List<IncludeRule> includeRules = otherRoot.getCheckoutRules().getRootIncludeRules();
    if (includeRules.isEmpty() && targetPath.isEmpty()) {
      return Collections.singletonList(targetPath);
    }

    for (IncludeRule rule : includeRules) {
      final String to = rule.getTo();
      if (targetPath.equals(to)) return Collections.singletonList(targetPath);

      if (targetPath.isEmpty() && !rule.isAbsolutePathTo()) {
        clashingPaths.add(to);
      } else if (to.startsWith(targetPath + "/")) {
        clashingPaths.add(to.substring(targetPath.length() + 1));
      }
    }

    final List<String> result = new ArrayList<>();
    clashingPaths.forEach(path -> {
      if (result.isEmpty() || !path.startsWith(result.get(result.size() - 1) + "/")) result.add(path);
    });
    return result;
  }
}
