

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.jetbrains.annotations.NotNull;

/**
 * The entry in submodule configuration
 */
public class Submodule {
  /**
   * The name of submodule name. It is usually a lowercase path.
   */
  private final String myName;
  /**
   * The path where submodule is mapped
   */
  private final String myPath;
  /**
   * The submodule URL.
   */
  private final String myUrl;

  /**
   * The entry constructor
   *
   * @param name the name of submodule
   * @param path the path in repository
   * @param url  the URL which is submodule is mapped to
   */
  Submodule(@NotNull String name, @NotNull String path, @NotNull String url) {
    myName = name;
    myUrl = url;
    myPath = path;
  }

  /**
   * @return the submodule name
   */
  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return the submodule path
   */
  @NotNull
  public String getPath() {
    return myPath;
  }

  /**
   * @return the submodule URL
   */
  @NotNull
  public String getUrl() {
    return myUrl;
  }
}