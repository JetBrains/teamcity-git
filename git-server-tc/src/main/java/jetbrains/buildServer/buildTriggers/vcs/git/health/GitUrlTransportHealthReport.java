package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReport;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports on the Git URL transport allowlist ({@link GitRemoteUrlInspector}): VCS roots currently broken
 * by it (per root, {@link ItemSeverity#ERROR}), and a global warning when a known-dangerous transport has
 * been explicitly permitted via the {@link Constants#ADDITIONAL_ALLOWED_URL_TRANSPORTS} override.
 */
public class GitUrlTransportHealthReport extends HealthStatusReport {

  public static final String TYPE = "GitUrlTransportHealthReport";

  private static final String DISALLOWED_TRANSPORT_CATEGORY_ID = TYPE + ".disallowedTransport";
  private static final String DANGEROUS_TRANSPORT_PERMITTED_ITEM_ID = TYPE + ".dangerousTransportPermitted";

  private static final String DATA_VCS_ROOT = "vcsRoot";
  private static final String DATA_URL = "url";
  private static final String DATA_URL_LABEL = "urlLabel";
  private static final String DATA_TRANSPORT = "transport";
  private static final String DATA_BUILD_TYPE = "buildType";
  private static final String DATA_TRANSPORTS = "transports";

  private static final Set<String> KNOWN_DANGEROUS_TRANSPORTS = new LinkedHashSet<>(Arrays.asList("ext", "fd"));

  private static final ItemCategory DANGEROUS_TRANSPORT_CATEGORY = new ItemCategory(
    TYPE + ".dangerousTransportPermittedCategory",
    "Git URL transport allowlist permits a dangerous transport",
    ItemSeverity.WARN
  );

  @NotNull
  @Override
  public String getType() {
    return TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Detect Git VCS roots using a disallowed URL transport";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Arrays.asList(createDisallowedTransportCategory(), DANGEROUS_TRANSPORT_CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.isItemWithSeverityAccepted(ItemSeverity.ERROR) || scope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (scope.isItemWithSeverityAccepted(ItemSeverity.ERROR)) {
      reportDisallowedTransportItems(scope, consumer);
    }
    if (scope.globalItems() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN)) {
      reportDangerousTransportPermittedItem(consumer);
    }
  }

  private void reportDisallowedTransportItems(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    final ItemCategory category = createDisallowedTransportCategory();

    for (SVcsRoot vcsRoot : scope.getVcsRoots()) {
      if (!isGitRoot(vcsRoot)) continue;

      GitVcsRootUrlWalker.walk(vcsRoot, scope, (root, buildType, urlType, url) -> {
        if (GitRemoteUrlInspector.verifyUrl(url) != GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT) return;

        final String urlLabel = Constants.FETCH_URL.equals(urlType) ? "fetch" : "push";
        final String transport = GitRemoteUrlInspector.getTransportName(url);
        final String identity = TYPE + "_root_" + root.getId() +
                                 "_BT_" + (buildType == null ? "none" : buildType.getExternalId()) +
                                 "_urlType_" + urlType;

        final ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
        data.put(DATA_VCS_ROOT, root);
        data.put(DATA_URL, url);
        data.put(DATA_URL_LABEL, urlLabel);
        data.put(DATA_TRANSPORT, transport == null ? "" : transport);
        if (buildType != null) data.put(DATA_BUILD_TYPE, buildType);

        consumer.consumeForVcsRoot(root, new HealthStatusItem(identity, category, data.build()));
      });
    }
  }

  private void reportDangerousTransportPermittedItem(@NotNull HealthStatusItemConsumer consumer) {
    Set<String> permitted = new LinkedHashSet<>(KNOWN_DANGEROUS_TRANSPORTS);
    permitted.retainAll(GitRemoteUrlInspector.getEffectiveAllowedTransports());
    if (permitted.isEmpty()) return;

    consumer.consumeGlobal(new HealthStatusItem(
      DANGEROUS_TRANSPORT_PERMITTED_ITEM_ID,
      DANGEROUS_TRANSPORT_CATEGORY,
      Collections.singletonMap(DATA_TRANSPORTS, (Object) permitted)
    ));
  }

  @NotNull
  private static ItemCategory createDisallowedTransportCategory() {
    return new ItemCategory(
      DISALLOWED_TRANSPORT_CATEGORY_ID,
      "Git VCS root uses a disallowed URL transport",
      ItemSeverity.ERROR,
      "The allowlist can be extended via the '" + Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS +
      "' internal property (comma-separated list). Currently allowed transports: " +
      StringUtil.join(", ", GitRemoteUrlInspector.getEffectiveAllowedTransports()) + ".",
      null
    );
  }

  private static boolean isGitRoot(@NotNull SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }
}
