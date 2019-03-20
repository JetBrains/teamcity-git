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

import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AuthSettings {

  private final VcsRoot myRoot;
  private final AuthenticationMethod myAuthMethod;
  private final boolean myIgnoreKnownHosts;
  private final URIishHelper myUrIishHelper;
  private final String myPassphrase;
  private final String myPrivateKeyFilePath;
  private final String myUserName;
  private final String myPassword;
  private final String myTeamCitySshKeyId;

  public AuthSettings(@NotNull VcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(root.getProperties(), root, urIishHelper);
  }

  public AuthSettings(@NotNull GitVcsRoot root, @NotNull URIishHelper urIishHelper) {
    this(root.getProperties(), root.getOriginalRoot(), urIishHelper);
  }

  public AuthSettings(@NotNull Map<String, String> properties, @NotNull URIishHelper urIishHelper) {
    this(properties, null, urIishHelper);
  }

  public AuthSettings(@NotNull Map<String, String> properties, @Nullable VcsRoot root, @NotNull URIishHelper urIishHelper) {
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
    myPassword = myAuthMethod != AuthenticationMethod.PASSWORD ? null : properties.get(Constants.PASSWORD);
    myTeamCitySshKeyId = myAuthMethod != AuthenticationMethod.TEAMCITY_SSH_KEY ? null : properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
    myRoot = root;
  }

  @Nullable
  public VcsRoot getRoot() {
    return myRoot;
  }

  private String readUsername(Map<String, String> properties) {
    if (myAuthMethod == AuthenticationMethod.ANONYMOUS) {
      return null;
    }
    String username = myUrIishHelper.getUserNameFromUrl(properties.get(Constants.FETCH_URL));
    String explicitUsername = properties.get(Constants.USERNAME);
    if (explicitUsername != null) {
      username = explicitUsername;
    }
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

  private static AuthenticationMethod readAuthMethod(Map<String, String> properties) {
    String method = properties.get(Constants.AUTH_METHOD);
    return method == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, method);
  }
}
