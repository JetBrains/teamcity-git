

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

  /**
   * Decides whether access to file-based git repositories should be blocked.
   * The blocking can mean that no new VCS roots with such URLs can be created.
   * It can also mean that accessing such repositories might throw an exception.
   * Development mode is taken into account for this decision.
   *
   * @return true if file-based git repositories should be blocked.
   * @see Constants#BLOCK_FILE_URL
   * @since 2026.1
   */
  boolean isBlockFileUrl();
}