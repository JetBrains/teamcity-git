package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import org.jetbrains.annotations.NotNull;

public class GitProxySettings {

  private final String myApiUrl;
  private final String myAuthToken;
  private final int myTimeoutMs;
  private final int myConnectTimeoutMs;
  private final int myConnectRetryCnt;

  /**
   * @param url git proxy api url
   * @param authToken auth token for the api
   * @param timoeutMs the response timeout for requests
   * @param connectTimeoutMs the timeout for establishing tcp connection with the server
   */
  public GitProxySettings(@NotNull String url, @NotNull String authToken, int timoeutMs, int connectTimeoutMs, int connectRetryCnt) {
    myApiUrl = url;
    myAuthToken = authToken;
    myTimeoutMs = timoeutMs;
    myConnectTimeoutMs = connectTimeoutMs;
    myConnectRetryCnt = connectRetryCnt;
  }

  @NotNull
  public String getUrl() {
    return myApiUrl;
  }

  @NotNull
  public String getAuthToken() {
    return myAuthToken;
  }

  public int getTimeoutMs() {
    return myTimeoutMs;
  }

  public int getConnectTimeoutMs() {
    return myConnectTimeoutMs;
  }

  public int getConnectRetryCnt() {
    return myConnectRetryCnt;
  }
}
