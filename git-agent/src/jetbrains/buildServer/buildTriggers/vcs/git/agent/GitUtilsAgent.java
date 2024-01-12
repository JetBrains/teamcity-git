

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
 * Some utility methods which used JGit library classes.
 * Several methods contains in the server GitServerUtil.
 * The methods are not in the common module to make the module do not depends on JGit library.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class GitUtilsAgent {

  public static String getShortBranchName(@NotNull String fullRefName) {
    if (isRegularBranch(fullRefName))
      return fullRefName.substring(Constants.R_HEADS.length());
    return fullRefName;
  }

  public static boolean isRegularBranch(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_HEADS);
  }

  public static boolean isTag(@NotNull Ref ref) {
    return isTag(ref.getName());
  }

  public static boolean isTag(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_TAGS);
  }

  public static boolean isAnonymousGitWithUsername(@NotNull URIish uri) {
    return "git".equals(uri.getScheme()) && uri.getUser() != null;
  }
}