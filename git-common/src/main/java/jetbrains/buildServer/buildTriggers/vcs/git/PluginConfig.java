

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public interface PluginConfig extends MirrorConfig {

  static int DEFAULT_IDLE_TIMEOUT = 1800;
  static int DEFAULT_SSH_CONNECT_TIMEOUT = 15; // seconds

  @NotNull
  File getCachesDir();

  int getIdleTimeoutSeconds();

  String getPathToGit();

  /**
   * Returns charset name for git output or null if the default charset should be used
   */
  @Nullable
  String getGitOutputCharsetName();

  int getSshConnectTimeoutSeconds();

}