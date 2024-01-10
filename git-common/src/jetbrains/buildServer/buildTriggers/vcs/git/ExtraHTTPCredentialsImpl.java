package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.function.Function;
import jetbrains.buildServer.connections.ExpiringAccessToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtraHTTPCredentialsImpl implements ExtraHTTPCredentials {

  @NotNull
  private final String myUrl;

  @Nullable
  private final String myUsername;

  @Nullable
  private final String myPassword;

  @Nullable
  private final String myTokenId;

  @Nullable
  private final Function<String, ExpiringAccessToken> myTokenRetriever;

  private final boolean myIsRefreshableToken;


  public ExtraHTTPCredentialsImpl( @NotNull String url,
                                   @Nullable String username,
                                   @Nullable String password) {
    myIsRefreshableToken = false;
    myUrl = url;
    myUsername = username;
    myPassword = password;
    myTokenId = null;
    myTokenRetriever = null;
  }

  public ExtraHTTPCredentialsImpl( @NotNull String url,
                                   @Nullable String username,
                                   @NotNull String tokenId,
                                   @NotNull  Function<String, ExpiringAccessToken> tokenRetriever) {
    myIsRefreshableToken = true;
    myUrl = url;
    myUsername = username;
    myPassword = null;
    myTokenId = tokenId;
    myTokenRetriever = tokenRetriever;
  }

  @NotNull
  @Override
  public String getUrl() {
    return myUrl;
  }

  @Nullable
  @Override
  public String getUsername() {
    return myUsername;
  }

  @Nullable
  @Override
  public String getPassword() {
    return myIsRefreshableToken ? null : myPassword;
  }

  @Nullable
  @Override
  public String getToken() {
    if (myIsRefreshableToken && myTokenId != null && myTokenRetriever != null) {
      ExpiringAccessToken myToken = myTokenRetriever.apply(myTokenId);
      if (myToken == null)
        return null;

      return myToken.getAccessToken();
    }

    return null;
  }

  @Override
  public boolean isRefreshableToken() {
    return myIsRefreshableToken;
  }
}
