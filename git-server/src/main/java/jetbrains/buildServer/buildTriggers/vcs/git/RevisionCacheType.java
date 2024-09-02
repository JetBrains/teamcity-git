

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public enum RevisionCacheType {

  HINT_CACHE("hint-cache"),

  COMMIT_CACHE("commit-cache");

  private final String myFileName;

  private RevisionCacheType(@NotNull String fileName) {
    myFileName = fileName;
  }

  public String getFileName() {
    return myFileName;
  }
}