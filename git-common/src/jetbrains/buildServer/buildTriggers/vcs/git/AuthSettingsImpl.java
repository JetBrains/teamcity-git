package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private String myPassword;
  private final String myTeamCitySshKeyId;
  private final Function<String, String> myTokenRetriever;
  private final Lock myTokenRetrievalLock = new ReentrantLock();

  public AuthSettingsImpl(@NotNull VcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(root.getProperties(), root, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull GitVcsRoot root, @NotNull URIishHelper urIishHelper, @Nullable Function <String, String> tokenRetriever) {
    this(root.getProperties(), root.getOriginalRoot(), urIishHelper, tokenRetriever);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @NotNull URIishHelper urIishHelper) {
    this(properties, null, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @Nullable VcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(properties, root, urIishHelper, null);
  }

  public AuthSettingsImpl(@NotNull Map<String, String> properties, @Nullable VcsRoot root, @NotNull URIishHelper urIishHelper, @Nullable Function <String, String> tokenRetriever) {
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
    String passwordValue = properties.get(Constants.PASSWORD);
    boolean isTokenId = isTokenId(passwordValue);
    myPassword = myAuthMethod != PASSWORD || isTokenId ? null : passwordValue;
    myTokenId = myAuthMethod != PASSWORD || !isTokenId ? null : passwordValue;
    myTeamCitySshKeyId = myAuthMethod != AuthenticationMethod.TEAMCITY_SSH_KEY ? null : properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
    myRoot = root;
    myTokenRetriever = tokenRetriever;
  }

  protected boolean isTokenId(@Nullable String passwordValue) {
    return false; // this is a common code, we can only distinguis between token ids and other values in server- and agent-specific code.
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
    if (myAuthMethod != PASSWORD || myTokenId == null || myTokenRetriever == null)
      return myPassword;

    myTokenRetrievalLock.lock();
    try {
      String newToken = myTokenRetriever.apply(myTokenId);
      if (!Objects.equals(newToken, myPassword)) {
        myPassword = newToken;
      }
    } finally {
      myTokenRetrievalLock.unlock();
    }

    return myPassword;
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
    result.put(Constants.PASSWORD, myPassword);
    filterNullValues(result);
    return result;
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
