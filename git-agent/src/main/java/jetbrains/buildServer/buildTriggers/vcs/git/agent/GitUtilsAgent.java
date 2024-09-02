

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.util.List;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.ExtraHTTPCredentials;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.PropertiesHelper;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.GIT_HTTP_CRED_PREFIX;

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

  public static List<ExtraHTTPCredentials> detectExtraHTTPCredentialsInBuild(@NotNull AgentRunningBuild build) {
    return GitUtils.processExtraHTTPCredentials(PropertiesHelper.aggregatePropertiesByAlias(build.getSharedConfigParameters(), GIT_HTTP_CRED_PREFIX));
  }
}