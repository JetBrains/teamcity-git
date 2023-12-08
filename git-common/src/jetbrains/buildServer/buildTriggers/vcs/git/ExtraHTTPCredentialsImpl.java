package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public class ExtraHTTPCredentialsImpl implements ExtraHTTPCredentials {
  private final String myUrl;
  private final String myUsername;
  private final String myPassword;

  private final boolean isRefreshableToken;


  public ExtraHTTPCredentialsImpl(String url, String username, String password) {
    myUrl = url;
    myUsername = username;
    myPassword = password;
    isRefreshableToken = false;
  }

  @NotNull
  @Override
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public String getUsername() {
    return myUsername;
  }

  @NotNull
  @Override
  public String getPassword() {
    return myPassword;
  }

  @Override
  public boolean isRefreshableToken() {
    return isRefreshableToken;
  }
}
