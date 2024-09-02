

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created 20.02.14 15:28
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public interface DotGitModulesResolver {
  @Nullable
  SubmodulesConfig forBlob(@NotNull AnyObjectId blob) throws IOException;
}