package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * @since 2021.1
 */
public enum AgentCheckoutPolicy {
  /**
   * Creates repository mirror on the agent machine and shares it between different builds with the same fetch URL. Most optimal approach for large repositories and long-lived agents.
   */
  USE_MIRRORS,

  /**
   * Hidden policy for backward compatibilty: same as USE_MIRRORS, but without referencing mirror objects folder via alternates
   */
  USE_MIRRORS_WITHOUT_ALTERNATES,

  /**
   * Performs checkout right into the checkout directory without creating a mirror. Less optimal in terms of disk usage than mirrors.
   */
  NO_MIRRORS,

  /**
   * Uses git shallow clone to checkout build revision (--depth 1). Ideal for short-lived agents.
   */
  SHALLOW_CLONE,

  /**
   * Uses shallow clone for short-lived agents and mirrors for regular long-lived agents.
   */
  AUTO
}
