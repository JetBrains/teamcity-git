

package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * Specify which files will be removed by 'git clean' command
 */
public enum AgentCleanFilesPolicy {
  /**
   * Only ignored unversioned files will be removed
   */
  IGNORED_ONLY,
  /**
   * Only non-ignored unversioned files will be removed
   */
  NON_IGNORED_ONLY,
  /**
   * All unversioned files will be removed
   */
  ALL_UNTRACKED
}