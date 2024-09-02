

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
* Created 20.02.14 12:35
*
* @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
*/
public class DotGitModulesResolverImpl implements DotGitModulesResolver {
  private static final Logger LOG = Logger.getInstance(DotGitModulesResolverImpl.class.getName());

  private final Repository myDb;

  public DotGitModulesResolverImpl(@NotNull final Repository db) {
    myDb = db;
  }

  @Nullable
  public SubmodulesConfig forBlob(@NotNull final AnyObjectId blob) throws IOException {
    try {
      return new SubmodulesConfig(myDb.getConfig(), new BlobBasedConfig(null, myDb, blob));
    } catch (ConfigInvalidException e) {
      LOG.info("Invalid submodule config: " + e.getMessage(), e);
      return null;
    }
  }
}