

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.buildTriggers.vcs.git.CommonURIish;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Configures remote repository
 */
public class RemoteRepositoryConfigurator {

  private boolean myExcludeUsernameFromHttpUrls;
  private File myGitDir;


  public void setExcludeUsernameFromHttpUrls(boolean excludeUsernameFromHttpUrls) {
    myExcludeUsernameFromHttpUrls = excludeUsernameFromHttpUrls;
  }


  public void setGitDir(@NotNull File gitDir) {
    myGitDir = gitDir;
  }


  /**
   * Configures and save the remote repository for specified VCS root
   * @param fetchUrl fetchUrl remote repository URL
   * @throws VcsException in case of any error
   */
  public void configure(@NotNull CommonURIish fetchUrl) throws VcsException {
    File gitDir = getGitDir();
    Repository repository = null;
    try {
      repository = new RepositoryBuilder().setGitDir(gitDir).build();
      StoredConfig config = repository.getConfig();
      String scheme = fetchUrl.getScheme();
      String user = fetchUrl.getUser();
      if (myExcludeUsernameFromHttpUrls && isHttp(scheme) && !StringUtil.isEmpty(user)) {
        URIish fetchUrlNoUser = ((URIish) fetchUrl.get()).setUser(null);
        config.setString("remote", "origin", "url", fetchUrlNoUser.toString());
        config.unset("credential", null, "username");
        config.setString("credential", fetchUrlNoUser.toString(), "username", user);
      } else {
        config.setString("remote", "origin", "url", fetchUrl.toString());
        config.unset("credential", null, "username");
      }
      config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
      config.save();
    } catch (Exception e) {
      throw new VcsException("Error while configuring remote repository at " + gitDir + ": " + e.getMessage(), e);
    } finally {
      if (repository != null)
        repository.close();
    }
  }


  private boolean isHttp(@Nullable String scheme) {
    return "http".equals(scheme) || "https".equals(scheme);
  }


  @NotNull
  private File getGitDir() {
    if (myGitDir != null)
      return myGitDir;
    throw new IllegalStateException("Git directory is not specified");
  }
}