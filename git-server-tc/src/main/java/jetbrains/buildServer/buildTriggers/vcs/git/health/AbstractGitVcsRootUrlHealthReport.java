package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReport;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * Base for a Health report that flags individual Git VCS root fetch/push URLs by predicate.
 */
abstract class AbstractGitVcsRootUrlHealthReport extends HealthStatusReport {

  private static final String DATA_VCS_ROOT = "vcsRoot";
  private static final String DATA_URL = "url";
  private static final String DATA_BUILD_TYPE = "buildType";

  @NotNull
  protected abstract ItemCategory getCategory();

  /** Whether the given already-resolved fetch/push URL value should be reported. */
  protected abstract boolean matches(@NotNull String url);

  /** Extra report-specific data to attach to the item, beyond the shared root/url/buildType keys. */
  @NotNull
  protected Map<String, Object> extraData(@NotNull String urlType, @NotNull String url) {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public final Collection<ItemCategory> getCategories() {
    return Collections.singletonList(getCategory());
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.isItemWithSeverityAccepted(getCategory().getSeverity());
  }

  protected final void reportMatchingUrls(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    final ItemCategory category = getCategory();

    for (SVcsRoot vcsRoot : scope.getVcsRoots()) {
      if (!isGitRoot(vcsRoot)) continue;

      GitVcsRootUrlResolver.forEachResolvedUrl(vcsRoot, scope, (root, buildType, urlType, url) -> {
        if (!matches(url)) return;

        final String identity = getType() + "_root_" + root.getId() +
                                 "_BT_" + (buildType == null ? "none" : buildType.getExternalId()) +
                                 "_urlType_" + urlType;

        final ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
        data.put(DATA_VCS_ROOT, root);
        data.put(DATA_URL, url);
        if (buildType != null) data.put(DATA_BUILD_TYPE, buildType);
        data.putAll(extraData(urlType, url));

        consumer.consumeForVcsRoot(root, new HealthStatusItem(identity, category, data.build()));
      });
    }
  }

  private static boolean isGitRoot(@NotNull SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }
}
