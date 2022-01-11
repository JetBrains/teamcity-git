package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod.ACCESS_TOKEN;
import static jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod.PASSWORD;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistry.isGitRoot;

public class GitHubPasswordAuthRootRegistryImpl implements GitHubPasswordAuthRootRegistry {

  private static final Logger LOG = Logger.getInstance(GitHubPasswordAuthRootRegistryImpl.class.getName());

  private static final String GITHUB_COM = "github.com";

  private static final String GITHUB_COM_PASSWORD_AUTH_USAGE_ADD = "gitHubPasswordAuthUsageAdd";
  private static final String GITHUB_COM_PASSWORD_AUTH_USAGE_REMOVE = "gitHubPasswordAuthUsageRemove";

  private final ConcurrentHashMap<Long, Long> myGitHubRootsWithPasswordAuth = new ConcurrentHashMap<>();

  private final MultiNodesEvents myMultiNodesEvents;
  private final ProjectManager myProjectManager;
  private final TimeService myTimeService;

  public GitHubPasswordAuthRootRegistryImpl(@NotNull EventDispatcher<BuildServerListener> buildServerEventDispatcher,
                                            @NotNull EventDispatcher<RepositoryStateListener> repositoryStateEventDispatcher,
                                            @NotNull ProjectManager projectManager,
                                            @NotNull ServerResponsibility serverResponsibility,
                                            @NotNull MultiNodesEvents multiNodesEvents,
                                            @NotNull TimeService timeService) {
    myMultiNodesEvents= multiNodesEvents;
    myProjectManager = projectManager;
    myTimeService = timeService;

    buildServerEventDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void vcsRootUpdated(@NotNull SVcsRoot oldVcsRoot, @NotNull SVcsRoot newVcsRoot) {
        if (serverResponsibility.canCheckForChanges() && isGitRoot(oldVcsRoot)) {
          removeVcsRoot(oldVcsRoot.getId(), true);
        }
      }

      @Override
      public void vcsRootRemoved(@NotNull SVcsRoot root) {
        if (serverResponsibility.canCheckForChanges() && isGitRoot(root)) {
          removeVcsRoot(root.getId(), true);
        }
      }
    });
    repositoryStateEventDispatcher.addListener(new RepositoryStateListenerAdapter() {
      @Override
      public void beforeRepositoryStateUpdate(@NotNull VcsRoot rootInstance, @NotNull RepositoryState oldState, @NotNull RepositoryState newState) {
        if (serverResponsibility.canCheckForChanges() && isGitRoot(rootInstance)) {
          update(rootInstance);
        }
      }
    });
    multiNodesEvents.subscribe(GITHUB_COM_PASSWORD_AUTH_USAGE_ADD, e -> {
      final Long rootId = e.getLongArg1();
      if (rootId == null) {
        LOG.warn(GITHUB_COM_PASSWORD_AUTH_USAGE_ADD + " multi-node event with unexpected null argument received: " + e);
        return;
      }
      addVcsRoot(rootId, false);
    });
    multiNodesEvents.subscribe(GITHUB_COM_PASSWORD_AUTH_USAGE_REMOVE, e -> {
      final Long rootId = e.getLongArg1();
      if (rootId == null) {
        LOG.warn(GITHUB_COM_PASSWORD_AUTH_USAGE_REMOVE + " multi-node event with unexpected null argument received: " + e);
        return;
      }
      removeVcsRoot(rootId, false);
    });
  }

  private static boolean isHexString(@NotNull String s) {
    for (char c : s.toCharArray()) {
      if (!isWordChar(c)) return false;
    }
    return true;
  }

  private static boolean isWordChar(char ch) {
    if (ch == '_') return true;
    return Character.isDigit(ch) || (Character.toUpperCase(ch) >= 'A' && Character.toUpperCase(ch) <= 'Z');
  }

  private static boolean isGitHubPasswordRoot(@NotNull VcsRoot root) {
    if (!isGitRoot(root)) return false;

    final String fetchUrl = root.getProperty(Constants.FETCH_URL, "");
    final String pushUrl = root.getProperty(Constants.PUSH_URL, "");
    String authMethod = root.getProperty(Constants.AUTH_METHOD);
    return (fetchUrl.contains(GITHUB_COM) || pushUrl.contains(GITHUB_COM))
           && PASSWORD.name().equals(authMethod);
  }

  @Nullable
  private static String getPasswordProperty(@NotNull VcsRoot root) {
    return root.getProperty(Constants.PASSWORD);
  }

  private static int getUpdateIntervalSec() {
    return TeamCityProperties.getInteger("teamcity.git.gitHubPasswordAuthHealthReport.updateIntervalSec", 300);
  }

  @TestOnly
  public Map<Long, Long> getRegistry() {
    return new HashMap<>(myGitHubRootsWithPasswordAuth);
  }

  @TestOnly
  public void update(@NotNull VcsRoot rootInstance) {
    final long rootId = rootInstance instanceof VcsRootInstance ? ((VcsRootInstance)rootInstance).getParentId() : rootInstance.getId();
    if (!isGitHubPasswordRoot(rootInstance)) {
      removeVcsRoot(rootId, null);
      return;
    }

    final String secret = getPasswordProperty(rootInstance);
    // if secret is a 40-hex character string we suppose it's a PAT, not password
    if (StringUtil.isNotEmpty(secret) && secret.length() == 40 && isHexString(secret)) {
      final SVcsRoot root = myProjectManager.findVcsRootById(rootId);
      final String passwordProp = root == null ? null : getPasswordProperty(root);

      if (StringUtil.isNotEmpty(passwordProp) && ReferencesResolverUtil.containsReference(passwordProp)) {
        final Long prev = myGitHubRootsWithPasswordAuth.get(rootId);
        // if password property contains a param reference we do not remove the root from the registry right away
        // to avoid report from flickering if parameter is resolved somewhere to a password and somewhere to a token
        if (prev != null && myTimeService.now() - prev > getUpdateIntervalSec()) {
          removeVcsRoot(rootId, true);
        }
      } else {
        removeVcsRoot(rootId, null);
      }
    } else {
      addVcsRoot(rootId, null);
    }
  }

  @Override
  public boolean containsVcsRoot(long id) {
    return myGitHubRootsWithPasswordAuth.containsKey(id);
  }

  private void addVcsRoot(long id, @Nullable Boolean sendMultinodeEvent) {
    final Long prev = myGitHubRootsWithPasswordAuth.put(id, myTimeService.now());
    if (Boolean.TRUE.equals(sendMultinodeEvent) || prev == null && sendMultinodeEvent == null) {
      myMultiNodesEvents.publish(GITHUB_COM_PASSWORD_AUTH_USAGE_ADD, id);
    }
  }

  private void removeVcsRoot(long id, @Nullable Boolean sendMultinodeEvent) {
    final Long prev = myGitHubRootsWithPasswordAuth.remove(id);
    if (Boolean.TRUE.equals(sendMultinodeEvent) || prev != null && sendMultinodeEvent == null) {
      myMultiNodesEvents.publish(GITHUB_COM_PASSWORD_AUTH_USAGE_REMOVE, id);
    }
  }

  //private static final HTTPRequestBuilder.RequestHandler REQUEST_HANDLER = new HTTPRequestBuilder.ApacheClient43RequestHandler();
  //public static final String API_GITHUB_COM = "https://api.github.com/";
  //
  //final Ref<Boolean> isPassword = new Ref<>(true);
  //try {
  //  IOGuard.allowNetworkCall(() -> REQUEST_HANDLER.doRequest(
  //    new HTTPRequestBuilder(API_GITHUB_COM)
  //      .withHeader("Authorization", secret)
  //      .withMethod(HttpMethod.GET)
  //      .withTimeout(10000)
  //      .withRetryCount(3)
  //      .onException(e -> {
  //        LOG.warnAndDebugDetails("Exception connecting to " + API_GITHUB_COM + " while checking VCS root " + LogUtil.describe(root), e);
  //      })
  //      .onErrorResponse(r -> {
  //        LOG.debug(API_GITHUB_COM + " returned " + r.getStatusCode() + ":" + r.getStatusText() + " for VCS root " + LogUtil.describe(root));
  //        LOG.debug(r.getBodyAsStringLimit(256 * 1024));
  //      })
  //      .onSuccess(r -> {
  //        LOG.debug(API_GITHUB_COM + " accepted token for VCS root " + LogUtil.describe(root));
  //        isPassword.set(false);
  //      })
  //      .build()));
  //} catch (URISyntaxException e) {
  //  LOG.warnAndDebugDetails("Failed to parse URI " + API_GITHUB_COM, e);
  //}
  //return isPassword.get();
}
