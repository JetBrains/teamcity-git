

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.usageStatistics.impl.providers.BaseVCSFeatureUsageStatisticsProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import org.jetbrains.annotations.NotNull;

public class GitSubmodulesUsageStatistics extends BaseVCSFeatureUsageStatisticsProvider {


  public GitSubmodulesUsageStatistics(@NotNull VcsManager vcsManager) {
    super(vcsManager);
  }

  @NotNull
  @Override
  protected String getFeatureName() {
    return "subRepoSupport-git";
  }

  @NotNull
  @Override
  protected String getFeatureDisplayName() {
    return "Git VCS roots with submodules support enabled";
  }

  @Override
  protected boolean hasFeature(@NotNull final SVcsRoot root) {
    String submoduleCheckout = root.getProperty(Constants.SUBMODULES_CHECKOUT);
    if (submoduleCheckout == null)
      return false;
    return !submoduleCheckout.equals(SubmodulesCheckoutPolicy.IGNORE.name());
  }
}