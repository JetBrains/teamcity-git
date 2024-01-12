

package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * The clean policy on the agent
 */
public enum AgentCleanPolicy {
  /**
   * Run 'git clean' if branch changes
   */
  ON_BRANCH_CHANGE,
  /**
   * Run 'git clean' always after checkout
   */
  ALWAYS,
  /**
   * Do not run 'git clean'
   */
  NEVER,
}