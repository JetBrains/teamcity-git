package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Reports Git VCS roots whose fetch/push URL is rejected by the transport allowlist
 * ({@link GitRemoteUrlInspector}) - a per-root problem, one {@link ItemCategory#getSeverity() ERROR} item per
 * broken URL. See {@link GitDangerousTransportPermittedHealthReport} for the separate, global warning about
 * the allowlist override itself permitting a known-dangerous transport.
 */
public class GitDisallowedTransportUrlHealthReport extends AbstractGitVcsRootUrlHealthReport {

  public static final String TYPE = "GitDisallowedTransportUrlHealthReport";

  private static final String DATA_URL_LABEL = "urlLabel";
  private static final String DATA_TRANSPORT = "transport";

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
  protected ItemCategory getCategory() {
    return new ItemCategory(
      TYPE + ".category",
      "Git VCS root uses a disallowed URL transport",
      ItemSeverity.ERROR,
      "The allowlist can be extended via the '" + Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS +
      "' internal property (comma-separated list). Currently allowed transports: " +
      StringUtil.join(", ", GitRemoteUrlInspector.getEffectiveAllowedTransports()) + ".",
      null
    );
  }

  @Override
  protected boolean matches(@NotNull String url) {
    return GitRemoteUrlInspector.verifyUrl(url) == GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT;
  }

  @NotNull
  @Override
  protected Map<String, Object> extraData(@NotNull String urlType, @NotNull String url) {
    final String urlLabel = Constants.FETCH_URL.equals(urlType) ? "fetch" : "push";
    final String transport = GitRemoteUrlInspector.getTransportName(url);
    return ImmutableMap.of(DATA_URL_LABEL, urlLabel, DATA_TRANSPORT, transport == null ? "" : transport);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    reportMatchingUrls(scope, consumer);
  }
}
