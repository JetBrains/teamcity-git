package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import jetbrains.buildServer.oauth.ExpiringAccessToken;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod.ACCESS_TOKEN;
import static jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod.PASSWORD;

public class AuthSettingsImpl implements AuthSettings {

  private final VcsRoot myRoot;
  private final AuthenticationMethod myAuthMethod;
  private final boolean myIgnoreKnownHosts;
  private final URIishHelper myUrIishHelper;
  private final String myPassphrase;
  private final String myPrivateKeyFilePath;
  private final String myUserName;
  private final String myTokenId;
  private ExpiringAccessToken myToken;
  private final String myPassword;
  private final String myTeamCitySshKeyId;
  private final Function<String, ExpiringAccessToken> myTokenRetriever;

  public AuthSettingsImpl(@NotNull VcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(root.getProperties(), root, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull GitVcsRoot root, @NotNull URIishHelper urIishHelper, @Nullable Function <String, ExpiringAccessToken> tokenRetriever) {
    this(root.getProperties(), root.getOriginalRoot(), urIishHelper, tokenRetriever);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @NotNull URIishHelper urIishHelper) {
    this(properties, null, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @Nullable VcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(properties, root, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @Nullable VcsRoot root, @NotNull URIishHelper urIishHelper, @Nullable Function <String, ExpiringAccessToken> tokenRetriever) {
    myAuthMethod = readAuthMethod(properties);
    myIgnoreKnownHosts = "true".equals(properties.get(Constants.IGNORE_KNOWN_HOSTS));
    if (myAuthMethod == AuthenticationMethod.PRIVATE_KEY_FILE) {
      myPassphrase = properties.get(Constants.PASSPHRASE);
      myPrivateKeyFilePath = properties.get(Constants.PRIVATE_KEY_PATH);
    } else if (myAuthMethod == AuthenticationMethod.TEAMCITY_SSH_KEY) {
      myPassphrase = properties.get(Constants.PASSPHRASE);
      myPrivateKeyFilePath = null;
    } else {
      myPassphrase = null;
      myPrivateKeyFilePath = null;
    }
    myUrIishHelper = urIishHelper;
    myUserName = readUsername(properties);
    if (myAuthMethod.isPasswordBased()) {
      myTokenId = properties.get(Constants.TOKEN_ID);
      myPassword = properties.get(Constants.PASSWORD);
    } else {
      myTokenId = null;
      myPassword = null;
    }
    myTeamCitySshKeyId = myAuthMethod != AuthenticationMethod.TEAMCITY_SSH_KEY ? null : properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
    myRoot = root;
    myTokenRetriever = tokenRetriever;
  }

  @Nullable
  @Override
  public VcsRoot getRoot() {
    return myRoot;
  }

  private String readUsername(Map<String, String> properties) {
    if (myAuthMethod == AuthenticationMethod.ANONYMOUS) {
      return null;
    }
    String explicitUsername = properties.get(Constants.USERNAME);
    return explicitUsername != null ? explicitUsername : myUrIishHelper.getUserNameFromUrl(properties.get(Constants.FETCH_URL));
  }

  @NotNull
  @Override
  public AuthenticationMethod getAuthMethod() {
    return myAuthMethod;
  }

  @Override
  public boolean isIgnoreKnownHosts() {
    return myIgnoreKnownHosts;
  }

  @Nullable
  @Override
  public String getPassphrase() {
    return myPassphrase;
  }

  @Nullable
  @Override
  public String getUserName() {
    return myUserName;
  }

  @Nullable
  @Override
  public String getPrivateKeyFilePath() {
    return myPrivateKeyFilePath;
  }

  @Nullable
  @Override
  public String getPassword() {
    if (myAuthMethod != ACCESS_TOKEN || myTokenId == null || myTokenRetriever == null)
      return myPassword;

    myToken = myTokenRetriever.apply(myTokenId);
    if (myToken == null)
      return null;

    return myToken.getAccessToken();
  }

  @Nullable
  @Override
  public String getTeamCitySshKeyId() {
    return myTeamCitySshKeyId;
  }

  @NotNull
  @Override
  public Map<String, String> toMap() {
    Map<String, String> result = new HashMap<String, String>();
    result.put(Constants.AUTH_METHOD, myAuthMethod.name());
    result.put(Constants.IGNORE_KNOWN_HOSTS, String.valueOf(myIgnoreKnownHosts));
    result.put(Constants.PASSPHRASE, myPassphrase);
    result.put(Constants.PRIVATE_KEY_PATH, myPrivateKeyFilePath);
    result.put(Constants.USERNAME, myUserName);
    result.put(Constants.PASSWORD, getPassword());
    filterNullValues(result);
    return result;
  }

  @Override
  public boolean doesTokenNeedRefresh() {
    return myTokenRetriever != null && myToken != null && myToken.isExpired();
  }

  private void filterNullValues(Map<String, String> map) {
    Iterator<Map.Entry<String, String>> iter = map.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, String> entry = iter.next();
      if (entry.getValue() == null) {
        iter.remove();
      }
    }
  }

  @NotNull
  private static AuthenticationMethod readAuthMethod(Map<String, String> properties) {
    String method = properties.get(Constants.AUTH_METHOD);
    return method == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, method);
  }
}
