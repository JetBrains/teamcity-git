

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public interface ContentLoaderFactory {
  @Nullable
  ObjectLoader open(@NotNull final Repository repo, @NotNull final ObjectId id) throws IOException;
}