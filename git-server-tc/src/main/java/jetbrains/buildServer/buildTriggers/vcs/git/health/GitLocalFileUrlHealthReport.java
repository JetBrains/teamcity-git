package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * Reports Git VCS roots that use local file access URLs in fetch/push configuration.
 * Such URLs are considered unsafe and may be unsupported in the future.
 */
public class GitLocalFileUrlHealthReport extends AbstractGitVcsRootUrlHealthReport {

  public static final String TYPE = "GitLocalFileUrlHealthReport";

  private static final ItemCategory CATEGORY = new ItemCategory(
    TYPE + ".category",
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
  protected ItemCategory getCategory() {
    return CATEGORY;
  }

  @Override
  protected boolean matches(@NotNull String url) {
    return GitRemoteUrlInspector.isLocalFileAccess(url);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (!TeamCityProperties.getBooleanOrTrue(Constants.WARN_FILE_URL)) {
      return;
    }
    reportMatchingUrls(scope, consumer);
  }
}
