

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Manages local mirror dirs of remote repositories
 * @author dmitry.neverov
 */
public interface MirrorManager {

  /**
   * @return parent dir of local repositories
   */
  @NotNull
  File getBaseMirrorsDir();

  /**
   * Get default directory for remote repository with specified url
   * @param repositoryUrl remote repository url
   * @return see above
   */
  @NotNull
  File getMirrorDir(@NotNull String repositoryUrl);

  /**
   * Mark dir as invalid, urls mapped to this dir will get another mapping
   * on subsequent call to getMirrorDir()
   * @param dir dir of interest
   */
  void invalidate(@NotNull File dir);

  /**
   * Removes mirror directory
   * @param dir directory to remove
   */
  void removeMirrorDir(@NotNull final File dir);


  @NotNull
  Map<String, File> getMappings();

  long getLastUsedTime(@NotNull final File dir);

  /**
   * Returns url for the given clone directory name inside the baseMirrorsDir
   * or null if mapping from the url is not found
   * @param cloneDirName clone directory name of interest
   * @return see above
   */
  @Nullable
  String getUrl(@NotNull String cloneDirName);

  /**
   * Checks is the provided mirror was marked as invalid
   * @param dirName folder to check
   * @return see above
   */
  boolean isInvalidDirName(@NotNull String dirName);
}