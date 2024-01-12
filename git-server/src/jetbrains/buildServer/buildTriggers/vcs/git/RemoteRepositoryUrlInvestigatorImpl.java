

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Implementation of {@link RemoteRepositoryUrlInvestigator}.
 * The same as RemoteRepositoryUrlInvestigatorImpl in the agent module.
 * The class are not in the common module to make the module do not depends on JGit library.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class RemoteRepositoryUrlInvestigatorImpl implements RemoteRepositoryUrlInvestigator {

  private final static Logger LOG = Logger.getInstance(RemoteRepositoryUrlInvestigatorImpl.class.getName());

  @Override
  @Nullable
  public String getRemoteRepositoryUrl(@NotNull final File repositoryDir) {
    try {
      Repository r = new RepositoryBuilder().setBare().setGitDir(repositoryDir).build();
      StoredConfig config = r.getConfig();
      String teamcityRemote = config.getString("teamcity", null, "remote");
      if (teamcityRemote != null)
        return teamcityRemote;
      return config.getString("remote", "origin", "url");
    } catch (Exception e) {
      LOG.warn("Error while trying to get remote repository url at " + repositoryDir.getAbsolutePath(), e);
      return null;
    }
  }
}