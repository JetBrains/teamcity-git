package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;

/**
 * The configuration constants
 */
public interface Constants {
  /**
   * The URL property
   */
  public static String URL = "url";
  /**
   * The path property
   */
  public static String PATH = "path";
  /**
   * The branch name property
   */
  public static String BRANCH_NAME = "branch";
  /**
   * The user name property
   */
  public static String USERNAME = "username";
  /**
   * The password property name
   */
  public static String PASSWORD = VcsRoot.SECURE_PROPERTY_PREFIX + "password";
  /**
   * The vcs name
   */
  public static String VCS_NAME = "git";
}
