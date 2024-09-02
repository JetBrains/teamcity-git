package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

public interface GitHubPasswordAuthRootRegistry {
  static boolean isGitRoot(@NotNull VcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  boolean containsVcsRoot(long vcsRootId);
}
