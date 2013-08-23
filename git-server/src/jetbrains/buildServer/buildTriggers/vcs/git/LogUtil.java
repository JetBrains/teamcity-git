package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.Nullable;

/**
 * Created 01.10.12 16:28
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class LogUtil {
  private static final String NULL_OBJECT = "<null>";

  /**
   * Describe VcsRoots in logs
   * @param root VCS root to describe
   * @return VCS root representation which allows to identify it among other VCS roots
   * */
  public static String describe(@Nullable final VcsRoot root) {
    return root == null ? NULL_OBJECT : root.toString();
  }

  public static String describe(@Nullable final GitVcsRoot root) {
    return root == null ? NULL_OBJECT : root.toString();
  }
}
