

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.util.StringUtil;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

/**
 * Util class for resolving submodule urls.
 *
 * @author Mikhail Khorkov
 * @since 2019.2
 */
public class SubmoduleUrlResolver {

  private SubmoduleUrlResolver() {
    throw new IllegalStateException();
  }

  /**
   * Check if url is absolute or relative.
   */
  public static boolean isAbsolute(@NotNull String url) {
    return !url.startsWith(".");
  }

  /**
   * Resolve submodule url by root module url.
   *
   * @throws URISyntaxException if case of main or submodule urls is wrong
   */
  @NotNull
  public static String resolveSubmoduleUrl(
    @NotNull ServerPluginConfig pluginConfig,
    @NotNull StoredConfig mainRepoConfig,
    @NotNull String submoduleUrl
  ) throws URISyntaxException {
    String mainRepoUrl = mainRepoConfig.getString("teamcity", null, "remote");
    URIish mainRepoUri = new URIish(mainRepoUrl);
    if (isAbsolute(submoduleUrl)) {
      String mainUser = mainRepoUri.getUser();
      URIish submoduleUri = new URIish(submoduleUrl);
      final String subUser = submoduleUri.getUser();
      if (StringUtil.isNotEmpty(mainUser)
          && StringUtil.isEmpty(subUser)
          && URIishHelperImpl.requiresCredentials(submoduleUri)
          && pluginConfig.shouldSetSubmoduleUserInAbsoluteUrls()
      ) {
        /* use main repo user as sub repo user only if sub repo user is empty */
        return submoduleUri.setUser(mainUser).toASCIIString();
      } else {
        return submoduleUrl;
      }
    }

    String newPath = mainRepoUri.getPath();
    if (newPath.length() == 0) {
      newPath = submoduleUrl;
    } else {
      newPath = GitUtils.normalizePath(newPath + '/' + submoduleUrl);
    }
    return mainRepoUri.setPath(newPath).toPrivateString();
  }
}