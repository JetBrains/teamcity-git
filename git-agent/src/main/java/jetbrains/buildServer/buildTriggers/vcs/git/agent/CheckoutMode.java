

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

public enum CheckoutMode {

  /**
   * Used for checkout rules of form:
   * +:.=>dir
   */
  MAP_REPO_TO_DIR,
  /**
   * Used for checkout rules of form:
   * +:path1
   * -:path2
   * Requires git version supporting sparse checkout
   */
  SPARSE_CHECKOUT

}