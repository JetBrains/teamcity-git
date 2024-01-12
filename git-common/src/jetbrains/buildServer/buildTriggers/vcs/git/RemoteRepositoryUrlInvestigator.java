

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Algorithm to get repository url from git repository.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public interface RemoteRepositoryUrlInvestigator {

  @Nullable
  String getRemoteRepositoryUrl(@NotNull File repositoryDir);
}