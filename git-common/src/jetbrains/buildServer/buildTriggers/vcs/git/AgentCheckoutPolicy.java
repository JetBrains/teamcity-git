package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * @since 2021.1
 */
public enum AgentCheckoutPolicy {
  /**
   * Cache remote repository on the agent machine under system/caches/git folder in order to speed up following checkouts.
   * Cache is reused between all builds using the same fetch URL.
   */
  USE_MIRRORS,

  /**
   * Hidden policy for backward compatibilty: same as USE_MIRRORS, but without referencing mirror objects folder via alternates
   */
  USE_MIRRORS_WITHOUT_ALTERNATES,

  /**
   * Hidden policy for backward compatibilty: do not cache repos on the agent machine, directly fetch build branch (or all branches according to build configuration) to the checkout directory.
   */
  NO_MIRRORS,

  /**
   * Do not cache repos on the agent machine, directly fetch single build revision to the checkout directory with depth=1.
   * This approach is expected to be the fasted way to get build revision from scratch.
   */
  SHALLOW_CLONE,

  /**
   * Default option: depending on the expected agent life cycle either use mirrors (for regular long-living agents) or shallow clone (for short-living agents).
   */
  AUTO
}
