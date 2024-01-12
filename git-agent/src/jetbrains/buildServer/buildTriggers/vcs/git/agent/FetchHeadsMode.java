

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