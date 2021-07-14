package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Map;
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
  public Map.Entry<String, String> getParameterWithExternalId(String paramBeforeExternalId) {
    VcsRoot currentRoot = root;

    while (currentRoot instanceof VcsRootInstance) {
      currentRoot = ((VcsRootInstance) currentRoot).getParent();
    }

    if (currentRoot instanceof SVcsRootEx) {
      SVcsRootEx currentRootEx = (SVcsRootEx) currentRoot;
      String externalId = currentRootEx.getExternalId();
      String parameter = currentRootEx.getProject().getParameterValue(paramBeforeExternalId + "." + externalId);

      return new java.util.AbstractMap.SimpleEntry<>(externalId, parameter);
    }
    else {
      throw new RuntimeException("Root error : todo normal exception");
    }
  }
}
