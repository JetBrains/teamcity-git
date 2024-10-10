package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.intellij.openapi.util.Pair;

public class ProxyCredentials {

  private final String myApiUrl;
  private final String myAuthToken;

  public ProxyCredentials(String url, String authToken) {
    myApiUrl = url;
    myAuthToken = authToken;
  }

  public String getUrl() {
    return myApiUrl;
  }

  public String getAuthToken() {
    return myAuthToken;
  }
}
