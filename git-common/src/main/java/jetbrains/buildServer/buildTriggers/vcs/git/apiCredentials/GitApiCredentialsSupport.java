package jetbrains.buildServer.buildTriggers.vcs.git.apiCredentials;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
import jetbrains.buildServer.vcs.ApiCredentialsSupport;
import jetbrains.buildServer.vcs.api_credentials.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitApiCredentialsSupport implements ApiCredentialsSupport {
  private static final Logger LOG = Logger.getInstance(GitApiCredentialsSupport.class.getName());

  @Override
  public void create(VcsRootImpl vcsRoot, @NotNull ApiCredential credential) {
    credential.putToPropertyMap(vcsRoot.getProperties());
  }

  @Override
  public @Nullable ApiCredential read(VcsRootImpl vcsRoot) {
    return getApiCredentialFromPropertyMap(vcsRoot.getProperties());
  }

  @Override
  public void update(VcsRootImpl vcsRoot, @NotNull ApiCredential credential) {
    ApiCredential oldCredential = getApiCredentialFromPropertyMap(vcsRoot.getProperties());
    if (oldCredential != null) {
      oldCredential.removeFromPropertyMap(vcsRoot.getProperties());
    }

    credential.putToPropertyMap(vcsRoot.getProperties());
  }

  @Override
  public void delete(VcsRootImpl vcsRoot) {
    ApiCredential credential = getApiCredentialFromPropertyMap(vcsRoot.getProperties());
    if (credential != null) {
      credential.removeFromPropertyMap(vcsRoot.getProperties());
    }
  }

  private @Nullable ApiCredential getApiCredentialFromPropertyMap(Map<String, String> properties) {
    String type = properties.get(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP);
    if (type == null) {
      return null;
    }

    try {
      ApiCredential apiCredential;
      switch (type) {
        case ConnectionApiCredential.API_CREDENTIAL_CONNECTION_TYPE:
          apiCredential = new ConnectionApiCredential(properties);
          break;
        case PersonalTokenApiCredential.API_CREDEDENTIAL_PERSONAL_TOKEN_TYPE:
          apiCredential = new PersonalTokenApiCredential(properties);
          break;
        default:
          LOG.warn(String.format("Unsupported API credential type %s", type));
          properties.remove(ApiCredential.API_CREDENTIAL_CREDENTIAL_TYPE_PROP);
          apiCredential = null;
      }
      return apiCredential;
    } catch (ApiCredentialNotFound e) {
      LOG.warn(String.format("Cannot find API credential type %s", type));
      return null;
    }
  }
}
