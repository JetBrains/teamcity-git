package jetbrains.buildServer.buildTriggers.vcs.git.tests.apiCredentials;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials.ConnectionApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredentialNotFound;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

@Test
public class ConnectionApiCredentialTest {
  private final String CONNECTION_ID = "connectionId";

  public void putToPropertyMap_mapContainsApiCredentialProperties() {
    ConnectionApiCredential credential = new ConnectionApiCredential(CONNECTION_ID);
    Map<String, String> properties = new HashMap<>();

    credential.putToPropertyMap(properties);

    assertEquals(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(CONNECTION_ID, properties.get(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP));
  }

  public void removeFromPropertyMap_mapContainsNoApiCredentialProperties() {
    Map<String, String> properties = new HashMap<>();
    setupConnectionApiCredentialProperties(properties);
    ConnectionApiCredential credential = new ConnectionApiCredential(CONNECTION_ID);

    credential.removeFromPropertyMap(properties);

    assertNull(properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertNull(properties.get(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP));
  }

  public void constructFromPropertyMap_mapContainsApiCredentialProperties_constructsSuccessfully() {
    Map<String, String> properties = new HashMap<>();
    setupConnectionApiCredentialProperties(properties);

    ConnectionApiCredential credential = new ConnectionApiCredential(properties);

    assertNotNull(credential);
    assertEquals(CONNECTION_ID, credential.getConnectionId());
  }

  @Test(expectedExceptions = ApiCredentialNotFound.class)
  public void constructFromPropertyMap_mapDoesNotContainApiCredentialProperties_throwsException() {
    Map<String, String> properties = new HashMap<>();

    new ConnectionApiCredential(properties);
  }

  private void setupConnectionApiCredentialProperties(Map<String, String> properties) {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE);
    properties.put(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP, CONNECTION_ID);
  }
}
