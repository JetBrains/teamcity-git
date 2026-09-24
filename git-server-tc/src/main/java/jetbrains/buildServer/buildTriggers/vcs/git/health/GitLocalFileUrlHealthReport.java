package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReport;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports Git VCS roots that use local file access URLs in fetch/push configuration.
 * Such URLs are considered unsafe and may be unsupported in the future.
 */
public class GitLocalFileUrlHealthReport extends HealthStatusReport {

  public static final String TYPE = "GitLocalFileUrlHealthReport";

  private static final String CATEGORY_ID = TYPE + ".category";

  private static final String DATA_URL = "url";
  private static final String DATA_VCS_ROOT = "vcsRoot";
  private static final String DATA_BUILD_TYPE = "buildType";

  private static final ItemCategory CATEGORY = new ItemCategory(
    CATEGORY_ID,
    "Git VCS root uses a local file URL",
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
    return "Detect Git VCS roots using local file URLs";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singletonList(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.isItemWithSeverityAccepted(CATEGORY.getSeverity());
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (!TeamCityProperties.getBooleanOrTrue(Constants.WARN_FILE_URL)) {
      return;
    }

    for (SVcsRoot vcsRoot : scope.getVcsRoots()) {
      if (!isGitRoot(vcsRoot)) continue;

      GitVcsRootUrlWalker.walk(vcsRoot, scope, (root, buildType, urlType, url) -> {
        if (!GitRemoteUrlInspector.isLocalFileAccess(url)) return;

        final ItemContext context = new ItemContext(root, buildType, urlType, url);
        consumer.consumeForVcsRoot(context.myRoot, new HealthStatusItem(context.toIdentity(), CATEGORY, context.toReportData()));
      });
    }
  }

  private static boolean isGitRoot(SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  private static class ItemContext {

    @NotNull
    private final SVcsRoot myRoot;

    @Nullable
    private final SBuildType myBuildType;

    @NotNull
    private final String myUrlType;

    @NotNull
    private final String myUrl;

    private ItemContext(@NotNull SVcsRoot root, @Nullable SBuildType buildType, @NotNull String urlType, @NotNull String url) {
      myRoot = root;
      myBuildType = buildType;
      myUrlType = urlType;
      myUrl = url;
    }

    @NotNull
    private String toIdentity() {
      return TYPE + "_root_" + myRoot.getId() +
             "_BT_" + (myBuildType == null ? "none" : myBuildType.getExternalId()) +
             "_urlType_" + myUrlType;
    }

    @NotNull
    private Map<String, Object> toReportData() {
      final ImmutableMap.Builder<String, Object> dataBuilder = ImmutableMap.builder();
      dataBuilder.put(DATA_VCS_ROOT, myRoot);
      dataBuilder.put(DATA_URL, myUrl);

      if (myBuildType != null) {
        dataBuilder.put(DATA_BUILD_TYPE, myBuildType);
      }

      return dataBuilder.build();
    }
  }
}
