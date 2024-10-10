package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitApiClientFactory extends GitApiClientFactoryBase {

  public GitApiClientFactory(@NotNull SSLTrustStoreProvider trustStoreProvider) {
    super(trustStoreProvider);
  }

  public GitRepoApi createRepoApi(ProxyCredentials proxyCredentials, @Nullable Map<String, String> headers, @NotNull String repo) {
    Map<String, String> actualHeaders = new HashMap<>();
    if (headers != null) {
      actualHeaders.putAll(headers);
    }
    actualHeaders.put(HttpHeaders.AUTHORIZATION, "Bearer " + proxyCredentials.getAuthToken());
    actualHeaders.put("X-Request-ID", "teamcity-" + UUID.randomUUID());
    return create(proxyCredentials.getUrl(), actualHeaders, repo, GitRepoApi.class, (args) -> {
      args.put("repo", repo);
    });
  }
}
