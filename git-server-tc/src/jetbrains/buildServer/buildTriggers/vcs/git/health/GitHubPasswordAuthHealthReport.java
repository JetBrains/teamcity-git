package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import java.net.URISyntaxException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.LogUtil;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

public class GitHubPasswordAuthHealthReport extends HealthStatusReport {

  public static final String API_GITHUB_COM = "https://api.github.com/";
  public static final ItemSeverity SEVERITY = ItemSeverity.WARN;
  public static final String VCS_ROOT_KEY = "vcsRoot";
  private static final Logger LOG = Logger.getInstance(GitHubPasswordAuthHealthReport.class.getName());
  private static final String GITHUB_COM = "github.com";
  private static final String PREFIX = "gitHubPasswordAuth";
  static final String REPORT_TYPE = PREFIX + "HealthReport";
  private static final ItemCategory CATEGORY = new ItemCategory(PREFIX + "HealthCategory",
                                                                "Vcs root uses password authentication with github.com", SEVERITY,
                                                                "VCS roots using deprecated password authentication with github.com will soon stop working",
                                                                "https://github.blog/2020-12-15-token-authentication-requirements-for-git-operations/#timeline");
  private static final HTTPRequestBuilder.RequestHandler REQUEST_HANDLER = new HTTPRequestBuilder.ApacheClient43RequestHandler();

  private static boolean isGitRoot(@NotNull SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return CATEGORY.getName();
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(CATEGORY);
  }

  private static boolean isEnabled() {
    return TeamCityProperties.getBoolean("teamcity.git." + REPORT_TYPE);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return isEnabled() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN) && scope.getVcsRoots().stream().anyMatch(GitHubPasswordAuthHealthReport::isGitRoot);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    if (!isEnabled()) return;

    final Map<String, List<String>> tokens = new HashMap<>();
    for (SVcsRoot root : scope.getVcsRoots()) {
      if (!isGitRoot(root)) continue;

      final String fetchUrl = root.getProperty(Constants.FETCH_URL, "");
      final String pushUrl = root.getProperty(Constants.PUSH_URL, "");
      if (!fetchUrl.contains(GITHUB_COM) &&
          !pushUrl.contains(GITHUB_COM) &&
          !AuthenticationMethod.PASSWORD.name().equals(root.getProperty(Constants.AUTH_METHOD))) {
        continue;
      }

      final String secret = root.getProperty(Constants.PASSWORD);
      if (StringUtil.isEmptyOrSpaces(secret)) continue;

      final Ref<Boolean> isPassword = new Ref<>(true);
      if (secret.length() == 40) {
        // can be a token, not password
        final String projectId = root.getProject().getProjectId();
        List<String> projectTokens = tokens.get(projectId);
        if (projectTokens == null) {
          projectTokens = new ArrayList<>();
          tokens.put(projectId, projectTokens);
        }
        if (projectTokens.contains(secret)) {
          isPassword.set(false);
        } else {
          try {
            IOGuard.allowNetworkCall(() -> REQUEST_HANDLER.doRequest(
              new HTTPRequestBuilder(API_GITHUB_COM)
                .withHeader("Authorization", secret)
                .withMethod(HttpMethod.GET)
                .withTimeout(30000)
                .withRetryCount(3)
                .onException(e -> {
                  LOG.warnAndDebugDetails("Exception connecting to " + API_GITHUB_COM + " while checking VCS root " + LogUtil.describe(root), e);
                })
                .onErrorResponse(r -> {
                  LOG.debug(API_GITHUB_COM + " returned " + r.getStatusCode() + ":" + r.getStatusText() + " for VCS root " + LogUtil.describe(root));
                  LOG.debug(r.getBodyAsStringLimit(256 * 1024));
                })
                .onSuccess(r -> {
                  LOG.debug(API_GITHUB_COM + " accepted token for VCS root " + LogUtil.describe(root));
                  isPassword.set(false);
                })
                .build()));
          } catch (URISyntaxException e) {
            LOG.warnAndDebugDetails("Failed to parse URI " + API_GITHUB_COM, e);
          }
          if (!isPassword.get()) {
            projectTokens.add(secret);
          }
        }
      }
      if (isPassword.get()) {
        Map<String, Object> additionalData = new HashMap<String, Object>();
        additionalData.put(VCS_ROOT_KEY, root);

        resultConsumer.consumeForVcsRoot(root, new HealthStatusItem(REPORT_TYPE + root.getId(), CATEGORY, SEVERITY, additionalData));
      }
    }
  }
}
