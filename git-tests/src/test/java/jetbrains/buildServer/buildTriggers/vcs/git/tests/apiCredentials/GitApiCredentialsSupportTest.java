package jetbrains.buildServer.buildTriggers.vcs.git.tests.apiCredentials;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials.GitApiCredentialsSupport;
import jetbrains.buildServer.vcs.ApiCredentialsSupport;
import jetbrains.buildServer.vcs.api_credentials.ApiCredential;
import jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials.ConnectionApiCredential;
import jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials.PersonalTokenApiCredential;
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
  private final ApiCredentialsSupport apiCredentialsSupport = new GitApiCredentialsSupport();
  private final Map<String, String> properties = new HashMap<String, String>();

  private final String CONNECTION_ID = "connectionId";
  private final String PERSONAL_TOKEN = "personalToken";

  private AutoCloseable mocks;

  @BeforeMethod
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    when(vcsRoot.getProperties()).thenReturn(properties);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    properties.clear();
    mocks.close();
  }

  public void read_connection_api_credential() {
    apiCredentialsSupport.create(vcsRoot, new ConnectionApiCredential(CONNECTION_ID));

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(ConnectionApiCredential.class, apiCredential.getClass());
  }

  public void read_personal_token_api_credential() {
    apiCredentialsSupport.create(vcsRoot, new PersonalTokenApiCredential(PERSONAL_TOKEN));

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(PersonalTokenApiCredential.class, apiCredential.getClass());
  }

  public void read_unsupported_type_api_credential() {
    properties.put(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP, "unsupported");

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
    assertNull(properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP));
  }

  public void read_empty_api_credential() {
    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }

  public void update_api_credential_from_connection_to_personal_token() {
    apiCredentialsSupport.create(vcsRoot, new ConnectionApiCredential(CONNECTION_ID));

    apiCredentialsSupport.update(vcsRoot, new PersonalTokenApiCredential(PERSONAL_TOKEN));
    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(PersonalTokenApiCredential.class, apiCredential.getClass());
  }

  public void update_api_credential_from_personal_token_to_connection() {
    apiCredentialsSupport.create(vcsRoot, new PersonalTokenApiCredential(PERSONAL_TOKEN));

    apiCredentialsSupport.update(vcsRoot, new ConnectionApiCredential(CONNECTION_ID));
    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNotNull(apiCredential);
    assertEquals(ConnectionApiCredential.class, apiCredential.getClass());
  }

  public void delete_connection_api_credential() {
    apiCredentialsSupport.create(vcsRoot, new ConnectionApiCredential(CONNECTION_ID));

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);
    assertNotNull(apiCredential);

    apiCredentialsSupport.delete(vcsRoot);
    apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }

  public void delete_personal_token_api_credential() {
    apiCredentialsSupport.create(vcsRoot, new PersonalTokenApiCredential(PERSONAL_TOKEN));

    ApiCredential apiCredential = apiCredentialsSupport.read(vcsRoot);
    assertNotNull(apiCredential);

    apiCredentialsSupport.delete(vcsRoot);
    apiCredential = apiCredentialsSupport.read(vcsRoot);

    assertNull(apiCredential);
  }
}
