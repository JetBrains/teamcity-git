package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsRootParametersExtractor {
  private final VcsRoot root;

  public VcsRootParametersExtractor(@NotNull VcsRoot root) {
    this.root = root;
  }

  public VcsRoot getRoot() {
    return root;
  }

  @Nullable
  public String getParameter(String param) {
    VcsRoot currentRoot = root;

    while (currentRoot instanceof VcsRootInstance) {
      currentRoot = ((VcsRootInstance) currentRoot).getParent();
    }

    if (currentRoot instanceof SVcsRootEx) {
      return ((SVcsRootEx) currentRoot).getProject().getParameterValue(param);
    }
    else {
      throw new RuntimeException("Root error : todo normal exception");
    }
  }
}
