

package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * Wrapper on JGit URIish. The class is need for make the common module do not depends on JGit.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public interface CommonURIish {

  /**
   * Get URIish.
   */
  <T> T get();

  String getScheme();

  String getHost();

  String toString();

  String toASCIIString();

  String getPath();

  String getUser();
}