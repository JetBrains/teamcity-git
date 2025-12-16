

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
   * Decides whether access to file-based git repositories should be allowed.
   * Not allowed can mean that no new VCS roots with such URLs can be created.
   * It can also mean that accessing such repositories might throw an exception.
   * In development mode this is always allowed.
   *
   * @return true if file-based git repositories are allowed.
   * @see Constants#ALLOW_FILE_URL
   * @since 2026.1
   */
  boolean isAllowFileUrl();
}