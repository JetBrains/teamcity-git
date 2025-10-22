package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.parameters.ValueResolver;
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
    if (!TeamCityProperties.getBoolean(Constants.WARN_FILE_URL)) {
      return;
    }

    for (SVcsRoot root : scope.getVcsRoots()) {
      if (!isGitRoot(root)) continue;

      reportForUrl(getFetchUrl(root), root, scope, consumer);
      reportForUrl(getPushUrl(root), root, scope, consumer);
    }
  }

  private static boolean isGitRoot(SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  @Nullable
  private static String getFetchUrl(@NotNull SVcsRoot root) {
    return root.getProperty(Constants.FETCH_URL);
  }

  @Nullable
  private static String getPushUrl(@NotNull SVcsRoot root) {
    return root.getProperty(Constants.PUSH_URL);
  }

  private static void reportForUrl(@Nullable String rawUrl, @NotNull SVcsRoot root, @NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (StringUtil.isEmpty(rawUrl)) return;

    if (!ReferencesResolverUtil.containsReference(rawUrl)) {
      checkAndReportForUrlValue(root, rawUrl, consumer);
      return;
    }

    if (scope.getBuildTypes().isEmpty()) {
      checkAndReportForProjectScope(root, rawUrl, consumer);
    }

    for (SBuildType buildConfig : scope.getBuildTypes()) {
      checkAndReportForBuildConfigScope(root, rawUrl, consumer, buildConfig);
    }
  }

  private static void checkAndReportForProjectScope(@NotNull SVcsRoot root, @NotNull String rawUrl, @NotNull HealthStatusItemConsumer consumer) {
    final String resolvedUrl = resolveUrl(rawUrl, root.getProject().getValueResolver());
    if (StringUtil.isEmpty(resolvedUrl)) return;

    checkAndReportForUrlValue(root, resolvedUrl, consumer);
  }

  private static void checkAndReportForBuildConfigScope(@NotNull SVcsRoot root,
                                                        @NotNull String rawUrl,
                                                        @NotNull HealthStatusItemConsumer consumer,
                                                        @NotNull SBuildType buildConfig) {
    final String resolvedUrl = resolveUrl(rawUrl, buildConfig.getValueResolver());
    if (StringUtil.isEmpty(resolvedUrl)) return;

    checkAndReportForUrlValue(root, resolvedUrl, consumer, buildConfig);
  }

  private static void checkAndReportForUrlValue(@NotNull SVcsRoot root, @NotNull String url, @NotNull HealthStatusItemConsumer consumer) {
    checkAndReportForUrlValue(root, url, consumer, null);
  }

  private static void checkAndReportForUrlValue(@NotNull SVcsRoot root, @NotNull String url, @NotNull HealthStatusItemConsumer consumer, @Nullable SBuildType buildConfig) {
    if (!checkUrlIsLocal(url)) return;

    final ImmutableMap.Builder<String, Object> dataBuilder = ImmutableMap.builder();
    dataBuilder.put(DATA_VCS_ROOT, root);
    dataBuilder.put(DATA_URL, url);

    if (buildConfig != null) {
      dataBuilder.put(DATA_BUILD_TYPE, buildConfig);
    }

    consumer.consumeForVcsRoot(root, new HealthStatusItem(identity(root, buildConfig), CATEGORY, dataBuilder.build()));
  }

  private static boolean checkUrlIsLocal(@NotNull String url) {
    return GitRemoteUrlInspector.isLocalFileAccess(url);
  }

  @Nullable
  private static String resolveUrl(@NotNull String rawUrl, @NotNull ValueResolver resolver) {
    final ProcessingResult result = resolver.resolve(rawUrl);
    if (result.isFullyResolved()) {
      return result.getResult();
    }

    return null;
  }

  @NotNull
  private static String identity(@NotNull SVcsRoot root, @Nullable SBuildType buildType) {
    return TYPE + "_root_" + root.getId() + "_BT_" + (buildType == null ? "none" : buildType.getExternalId());
  }
}
