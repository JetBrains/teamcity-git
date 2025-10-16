package jetbrains.buildServer.buildTriggers.vcs.git.tests.apiCredentials;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials.PersonalTokenApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ApiCredentialNotFound;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;
import static org.testng.AssertJUnit.assertEquals;

@Test
public class PersonalTokenApiCredentialTest {
  private final String PERSONAL_TOKEN = "personalToken";

  public void putToPropertyMap_mapContainsApiCredentialProperties() {
    PersonalTokenApiCredential credential = new PersonalTokenApiCredential(PERSONAL_TOKEN);
    Map<String, String> properties = new HashMap<>();

    credential.putToPropertyMap(properties);

    assertEquals(PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(PERSONAL_TOKEN, properties.get(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP));
  }

  public void removeFromPropertyMap_mapContainsNoApiCredentialProperties() {
    Map<String, String> properties = new HashMap<>();
    setupPersonalTokenApiCredentialProperties(properties);
    PersonalTokenApiCredential credential = new PersonalTokenApiCredential(PERSONAL_TOKEN);

    credential.removeFromPropertyMap(properties);

    assertNull(properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertNull(properties.get(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP));
  }

  public void constructFromPropertyMap_mapContainsApiCredentialProperties_constructsSuccessfully() {
    Map<String, String> properties = new HashMap<>();
    setupPersonalTokenApiCredentialProperties(properties);

    PersonalTokenApiCredential credential = new PersonalTokenApiCredential(properties);

    assertNotNull(credential);
    assertEquals(PERSONAL_TOKEN, credential.getToken());
  }

  @Test(expectedExceptions = ApiCredentialNotFound.class)
  public void constructFromPropertyMap_mapDoesNotContainApiCredentialProperties_throwsException() {
    Map<String, String> properties = new HashMap<>();

    new PersonalTokenApiCredential(properties);
  }

  private void setupPersonalTokenApiCredentialProperties(Map<String, String> properties) {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE);
    properties.put(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP, PERSONAL_TOKEN);
  }
}
