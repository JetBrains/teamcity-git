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

  public GitApiClient<GitRepoApi> createRepoApi(GitProxySettings proxyCredentials, @Nullable Map<String, String> headers, @NotNull String repo) {
    Map<String, String> actualHeaders = new HashMap<>();
    if (headers != null) {
      actualHeaders.putAll(headers);
    }
    actualHeaders.put(HttpHeaders.AUTHORIZATION, "Bearer " + proxyCredentials.getAuthToken());
    actualHeaders.put(HttpHeaders.CONNECTION, "keep-alive");
    return create(proxyCredentials, actualHeaders, repo, GitRepoApi.class, (args) -> {
      args.put("repo", repo);
    });
  }
}
