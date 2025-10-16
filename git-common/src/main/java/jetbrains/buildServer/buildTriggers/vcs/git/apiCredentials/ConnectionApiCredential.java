package jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials;

import java.util.Map;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredentialNotFound;
import org.jetbrains.annotations.NotNull;

public class ConnectionApiCredential implements ApiCredential {
  public static final String API_CREDENTIAL_CONNECTION_TYPE = "connection";
  public static final String API_CREDENTIAL_CONNECTION_ID_PROP = "apiCredentials.connectionId";

  @NotNull
  private final String connectionId;

  public ConnectionApiCredential(@NotNull String connectionId) {
    this.connectionId = connectionId;
  }

  public ConnectionApiCredential(Map<String, String> properties) {
    String connectionId = properties.get(API_CREDENTIAL_CONNECTION_ID_PROP);

    if (connectionId == null) {
      removeFromPropertyMap(properties);
      throw new ApiCredentialNotFound();
    }

    this.connectionId = connectionId;
  }

  @Override
  public void putToPropertyMap(@NotNull Map<String, String> properties) {
    properties.put(API_CREDENTIAL_CREDENTIAL_TYPE_PROP, API_CREDENTIAL_CONNECTION_TYPE);
    properties.put(API_CREDENTIAL_CONNECTION_ID_PROP, connectionId);
  }

  @Override
  public void removeFromPropertyMap(@NotNull Map<String, String> properties) {
    properties.remove(API_CREDENTIAL_CREDENTIAL_TYPE_PROP);
    properties.remove(API_CREDENTIAL_CONNECTION_ID_PROP);
  }

  @NotNull
  public String getConnectionId() {
    return connectionId;
  }
}
