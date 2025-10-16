package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitApiCredentialsSupport;
import jetbrains.buildServer.vcs.ApiCredentialsSupport;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.vcs.api_credentials.ConnectionApiCredential;
import jetbrains.buildServer.vcs.api_credentials.PersonalTokenApiCredential;
import jetbrains.buildServer.vcs.api_credentials.RefreshableTokenApiCredential;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.*;

@Test
public class GitApiCredentialsSupportTest {
  @Mock
  private VcsRootImpl vcsRoot;

  private ApiCredentialsSupport apiCredentialsSupport;

  private final Map<String, String> properties = new HashMap<String, String>();

  private final String CONNECTION_ID = "connectionId";
  private final String PERSONAL_TOKEN = "personalToken";
  private final String ACCESS_TOKEN = "accessToken";
  private final String REFRESH_TOKEN = "refreshToken";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    apiCredentialsSupport = new GitApiCredentialsSupport();

    when(vcsRoot.getProperties()).thenReturn(properties);
  }

  @AfterMethod
  public void tearDown() {
    properties.clear();
  }

  public void create_connection_api_credential() {
    ApiCredential credential = new ConnectionApiCredential(CONNECTION_ID);

    apiCredentialsSupport.create(vcsRoot, credential);

    assertEquals(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(CONNECTION_ID, properties.get(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP));
  }

  public void create_personal_token_api_credential() {
    ApiCredential credential = new PersonalTokenApiCredential(PERSONAL_TOKEN);

    apiCredentialsSupport.create(vcsRoot, credential);

    assertEquals(PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(PERSONAL_TOKEN, properties.get(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP));
  }

  public void create_refresh_token_api_credential() {
    ApiCredential credential = new RefreshableTokenApiCredential(ACCESS_TOKEN, REFRESH_TOKEN);

    apiCredentialsSupport.create(vcsRoot, credential);

    assertEquals(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESHABLE_TOKEN_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(ACCESS_TOKEN, properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_ACCESS_TOKEN_PROP));
    assertEquals(REFRESH_TOKEN, properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESH_TOKEN_PROP));
  }

  public void read_connection_api_credential() {
    setupConnectionApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(ConnectionApiCredential.class, apiCredential.getClass());
  }

  public void read_personal_token_api_credential() {
    setupPersonalTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(PersonalTokenApiCredential.class, apiCredential.getClass());
  }

  public void read_refreshable_token_api_credential() {
    setupRefreshableTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(RefreshableTokenApiCredential.class, apiCredential.getClass());
  }

  public void read_unsupported_type_api_credential() {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, "unsupported");
    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }

  public void read_empty_api_credential() {
    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }

  public void update_api_credential_from_connection_to_personal_token() {
    setupConnectionApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);

    ApiCredential newCredential = new PersonalTokenApiCredential(PERSONAL_TOKEN);
    apiCredentialsSupport.update(vcsRoot, newCredential);

    assertEquals(PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(PERSONAL_TOKEN, properties.get(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP));
    assertNull(properties.get(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP));
  }

  public void update_api_credential_from_personal_token_to_refreshable_token() {
    setupPersonalTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);

    ApiCredential newCredential = new RefreshableTokenApiCredential(ACCESS_TOKEN, REFRESH_TOKEN);
    apiCredentialsSupport.update(vcsRoot, newCredential);

    assertEquals(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESHABLE_TOKEN_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(ACCESS_TOKEN, properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_ACCESS_TOKEN_PROP));
    assertEquals(REFRESH_TOKEN, properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESH_TOKEN_PROP));
    assertNull(properties.get(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP));
  }

  public void update_api_credential_from_refreshable_token_to_connection() {
    setupRefreshableTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);

    ApiCredential newCredential = new ConnectionApiCredential(CONNECTION_ID);
    apiCredentialsSupport.update(vcsRoot, newCredential);

    assertEquals(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE, properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
    assertEquals(CONNECTION_ID, properties.get(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP));
    assertNull(properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_ACCESS_TOKEN_PROP));
    assertNull(properties.get(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESH_TOKEN_PROP));
  }

  public void delete_connection_api_credential() {
    setupConnectionApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(ConnectionApiCredential.class, apiCredential.getClass());

    apiCredentialsSupport.delete(vcsRoot);

    apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }

  public void delete_personal_token_api_credential() {
    setupPersonalTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(PersonalTokenApiCredential.class, apiCredential.getClass());
  }

  public void delete_refreshable_token_api_credential() {
    setupRefreshableTokenApiCredentialProperties();

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(RefreshableTokenApiCredential.class, apiCredential.getClass());
  }

  private void setupConnectionApiCredentialProperties() {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE);
    properties.put(ConnectionApiCredential.API_CREDENTIAL_CONNECTION_ID_PROP, CONNECTION_ID);
  }

  private void setupPersonalTokenApiCredentialProperties() {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE);
    properties.put(PersonalTokenApiCredential.API_CREDENTIAL_PERSONAL_TOKEN_PROP, PERSONAL_TOKEN);
  }

  private void setupRefreshableTokenApiCredentialProperties() {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, RefreshableTokenApiCredential.API_CREDENTIAL_REFRESHABLE_TOKEN_TYPE);
    properties.put(RefreshableTokenApiCredential.API_CREDENTIAL_ACCESS_TOKEN_PROP, ACCESS_TOKEN);
    properties.put(RefreshableTokenApiCredential.API_CREDENTIAL_REFRESH_TOKEN_PROP, REFRESH_TOKEN);
  }
}
