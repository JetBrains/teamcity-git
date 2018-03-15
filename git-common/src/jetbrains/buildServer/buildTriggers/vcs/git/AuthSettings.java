/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AuthSettings {

  private final VcsRoot myRoot;
  private final AuthenticationMethod myAuthMethod;
  private final boolean myIgnoreKnownHosts;
  private final String myPassphrase;
  private final String myPrivateKeyFilePath;
  private final String myUserName;
  private final String myPassword;
  private final String myTeamCitySshKeyId;

  public AuthSettings(@NotNull VcsRoot root) {
    this(root.getProperties(), root);
  }

  public AuthSettings(@NotNull GitVcsRoot root) {
    this(root.getProperties(), root.getOriginalRoot());
  }

  public AuthSettings(@NotNull Map<String, String> properties) {
    this(properties, null);
  }

  public AuthSettings(@NotNull Map<String, String> properties, @Nullable VcsRoot root) {
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
    myUserName = readUsername(properties);
    myPassword = myAuthMethod != AuthenticationMethod.PASSWORD ? null : properties.get(Constants.PASSWORD);
    myTeamCitySshKeyId = myAuthMethod != AuthenticationMethod.TEAMCITY_SSH_KEY ? null : properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
    myRoot = root;
  }

  @Nullable
  public VcsRoot getRoot() {
    return myRoot;
  }

  private String readUsername(Map<String, String> properties) {
    if (myAuthMethod == AuthenticationMethod.ANONYMOUS)
      return null;
    String url = properties.get(Constants.FETCH_URL);
    String username = null;
    try {
      URIish u = new URIish(url);
      username = u.getUser();
    } catch (URISyntaxException e) {
      //ignore
    }
    String explicitUsername = properties.get(Constants.USERNAME);
    if (explicitUsername != null)
      username = explicitUsername;
    return username;
  }

  public AuthenticationMethod getAuthMethod() {
    return myAuthMethod;
  }

  public boolean isIgnoreKnownHosts() {
    return myIgnoreKnownHosts;
  }

  public String getPassphrase() {
    return myPassphrase;
  }

  public String getUserName() {
    return myUserName;
  }

  public String getPrivateKeyFilePath() {
    return myPrivateKeyFilePath;
  }

  public String getPassword() {
    return myPassword;
  }

  @Nullable
  public String getTeamCitySshKeyId() {
    return myTeamCitySshKeyId;
  }

  public URIish createAuthURI(@Nullable String uri) throws VcsException {
    return createAuthURI(uri, true);
  }

  public URIish createAuthURI(@Nullable String uri, boolean fixErrors) throws VcsException {
    URIish result;
    try {
      result = new URIish(uri);
    } catch (Exception e) {
      if (uri != null && ReferencesResolverUtil.containsReference(uri))
        throw new VcsException("Unresolved parameter in url: " + uri, e);
      throw new VcsException("Invalid URI: " + uri, e);
    }
    return createAuthURI(result, fixErrors);
  }

  public URIish createAuthURI(final URIish uri) {
    return createAuthURI(uri, true);
  }

  public URIish createAuthURI(final URIish uri, boolean fixErrors) {
    URIish result = uri;
    if (requiresCredentials(result)) {
      if (!StringUtil.isEmptyOrSpaces(myUserName)) {
        result = result.setUser(myUserName);
      }
      if (!StringUtil.isEmpty(myPassword)) {
        result = result.setPass(myPassword);
      }
    }
    if (fixErrors && isAnonymousProtocol(result)) {
      result = result.setUser(null);
      result = result.setPass(null);
    }
    return result;
  }

  private boolean isAnonymousProtocol(@NotNull URIish uriish) {
    return "git".equals(uriish.getScheme());
  }

  public static boolean requiresCredentials(URIish result) {
    String scheme = result.getScheme();
    return result.getHost() != null ||
           scheme != null && !scheme.equals("git");
  }

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

  public CredentialsProvider toCredentialsProvider() {
    return new CredentialsProvider() {
      @Override
      public boolean isInteractive() {
        return false;
      }
      @Override
      public boolean supports(CredentialItem... items) {
        for (CredentialItem i : items) {
          if (i instanceof CredentialItem.Username && myAuthMethod != AuthenticationMethod.ANONYMOUS)
            continue;
          if (i instanceof CredentialItem.Password && myAuthMethod == AuthenticationMethod.PASSWORD && myPassword != null)
            continue;
          return false;
        }
        return true;
      }

      @Override
      public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        boolean allValuesSupplied = true;
        for (CredentialItem i : items) {
          if (i instanceof CredentialItem.Username) {
            allValuesSupplied &= supplyUsername(uri, (CredentialItem.Username) i);
          } else if (i instanceof CredentialItem.Password) {
            allValuesSupplied &= supplyPassword((CredentialItem.Password) i);
          } else if (i instanceof CredentialItem.StringType && "Passphrase for ".equals(i.getPromptText())) {
            //we provider a passphrase to the jsch, if we are asked about it
            //then the original passphrase was incorrect
            throw new WrongPassphraseException(uri);
          } else {
            throw new UnsupportedCredentialItem(uri, i.getPromptText());
          }
        }
        return allValuesSupplied;
      }

      private boolean supplyUsername(URIish uri, CredentialItem.Username item) {
        if (myAuthMethod == AuthenticationMethod.ANONYMOUS)
          return false;
        String username = myUserName != null ? myUserName : uri.getUser();
        if (username == null)
          return false;
        item.setValue(username);
        return true;
      }

      private boolean supplyPassword(CredentialItem.Password item) {
        if (myAuthMethod != AuthenticationMethod.PASSWORD)
          return false;
        if (myPassword == null)
          return false;
        item.setValue(myPassword.toCharArray());
        return true;
      }
    };
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

  private static AuthenticationMethod readAuthMethod(Map<String, String> properties) {
    String method = properties.get(Constants.AUTH_METHOD);
    return method == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, method);
  }
}
