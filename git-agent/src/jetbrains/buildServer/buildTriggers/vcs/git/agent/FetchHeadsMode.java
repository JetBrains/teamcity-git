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

/**
 * Specifies when all heads should be fetched on the agent.
 */
enum FetchHeadsMode {
  /**
   * If build revision is not found on the agent, the build's branch should
   * be fetched first. If commit is still not found, then all heads should
   * be fetched. This mode is used by default.
   */
  AFTER_BUILD_BRANCH,
  /**
   * If build revision is not found on the agent, all heads should be fetched.
   * If commit is still not found and build's branch is not under refs/heads/, then
   * the build's branch should be fetched as well.
   */
  BEFORE_BUILD_BRANCH,
  /**
   * Always fetch all branches, even if commit is found on the agent. If commit is
   * not found after all branches fetch and build's branch is not under refs/heads/, then
   * the build's branch should be fetched as well.
   */
  ALWAYS
}
