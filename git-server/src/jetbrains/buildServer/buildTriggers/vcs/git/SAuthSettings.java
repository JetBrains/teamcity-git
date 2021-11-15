package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage.TOKEN_ID_PREFIX;

public class SAuthSettings extends AuthSettingsImpl implements AuthSettings {

  public SAuthSettings(@NotNull GitVcsRoot root, @NotNull URIishHelper urIishHelper,  @Nullable Function<String, String> tokenRetriever) {
    super(root, urIishHelper, tokenRetriever);
  }

  @Override
  protected boolean isTokenId(@Nullable String passwordValue) {
    return passwordValue != null && passwordValue.startsWith(TOKEN_ID_PREFIX);
  }
}
