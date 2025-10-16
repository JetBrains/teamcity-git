package jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials;

import java.util.Map;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredentialNotFound;
import org.jetbrains.annotations.NotNull;

public class PersonalTokenApiCredential implements ApiCredential {
  public static final String API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE = "personalToken";
  public static final String API_CREDENTIAL_PERSONAL_TOKEN_PROP = "apiCredentials.personalToken";

  @NotNull
  private final String token;

  public PersonalTokenApiCredential(@NotNull String token) {
    this.token = token;
  }

  public PersonalTokenApiCredential(Map<String, String> properties) throws ApiCredentialNotFound {
    String token = properties.get(API_CREDENTIAL_PERSONAL_TOKEN_PROP);
    if (token == null) {
      removeFromPropertyMap(properties);
      throw new ApiCredentialNotFound();
    }
    this.token = token;
  }

  @Override
  public void putToPropertyMap(@NotNull Map<String, String> properties) {
    properties.put(API_CREDENTIAL_CREDENTIAL_TYPE_PROP, API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE);
    properties.put(API_CREDENTIAL_PERSONAL_TOKEN_PROP, token);
  }

  @Override
  public void removeFromPropertyMap(@NotNull Map<String, String> properties) {
    properties.remove(API_CREDENTIAL_CREDENTIAL_TYPE_PROP);
    properties.remove(API_CREDENTIAL_PERSONAL_TOKEN_PROP);
  }

  @NotNull
  public String getToken() {
    return token;
  }
}
