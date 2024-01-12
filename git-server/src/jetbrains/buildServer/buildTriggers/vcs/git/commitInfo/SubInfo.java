

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Created 20.02.14 14:35
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public interface SubInfo {
  @NotNull
  Map<String, AnyObjectId> getSubmoduleToPath();

  @NotNull
  SubmodulesConfig getConfig();
}