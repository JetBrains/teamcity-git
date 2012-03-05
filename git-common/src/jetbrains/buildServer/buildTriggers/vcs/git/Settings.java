/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Git Vcs Settings
 */
public class Settings {

  private final MirrorManager myMirrorManager;
  private final URIish myRepositoryFetchURL;
  private final URIish myRepositoryPushURL;
  private final String myRef;
  private final UserNameStyle myUsernameStyle;
  private final SubmodulesCheckoutPolicy mySubmodulePolicy;
  private final AuthSettings myAuthSettings;
  private final String myUsernameForTags;
  private File myUserDefinedRepositoryPath;

  public Settings(@NotNull final MirrorManager mirrorManager, final VcsRoot root) throws VcsException {
    myMirrorManager = mirrorManager;
    myUserDefinedRepositoryPath = readPath(root);
    myRef = root.getProperty(Constants.BRANCH_NAME);
    myUsernameStyle = readUserNameStyle(root);
    mySubmodulePolicy = readSubmodulesPolicy(root);
    myAuthSettings = new AuthSettings(root.getProperties());
    myRepositoryFetchURL = myAuthSettings.createAuthURI(root.getProperty(Constants.FETCH_URL));
    final String pushUrl = root.getProperty(Constants.PUSH_URL);
    myRepositoryPushURL = StringUtil.isEmpty(pushUrl) ? myRepositoryFetchURL : myAuthSettings.createAuthURI(pushUrl);
    myUsernameForTags = root.getProperty(Constants.USERNAME_FOR_TAGS);
  }

  @NotNull
  public PersonIdent getTagger(@NotNull Repository r) {
    if (myUsernameForTags == null)
      return new PersonIdent(r);
    return parseIdent();
  }

  private static File readPath(VcsRoot root) {
    String path = root.getProperty(Constants.PATH);
    return path == null ? null : new File(path);
  }

  private static UserNameStyle readUserNameStyle(VcsRoot root) {
    final String style = root.getProperty(Constants.USERNAME_STYLE);
    if (style == null) {
      return UserNameStyle.USERID;
    } else {
      return Enum.valueOf(UserNameStyle.class, style);
    }
  }

  private static SubmodulesCheckoutPolicy readSubmodulesPolicy(VcsRoot root) {
    final String submoduleCheckout = root.getProperty(Constants.SUBMODULES_CHECKOUT);
    if (submoduleCheckout == null) {
      return SubmodulesCheckoutPolicy.IGNORE;
    } else {
      return Enum.valueOf(SubmodulesCheckoutPolicy.class, submoduleCheckout);
    }
  }

  /**
   * @return true if submodules should be checked out
   */
  public boolean isCheckoutSubmodules() {
    return mySubmodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS;
  }

  public SubmodulesCheckoutPolicy getSubmodulesCheckoutPolicy() {
    return mySubmodulePolicy;
  }

  public UserNameStyle getUsernameStyle() {
    return myUsernameStyle;
  }

  public File getRepositoryDir() {
    String fetchUrl = getRepositoryFetchURL().toString();
    return myUserDefinedRepositoryPath != null ? myUserDefinedRepositoryPath : myMirrorManager.getMirrorDir(fetchUrl);
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setUserDefinedRepositoryPath(File file) {
    myUserDefinedRepositoryPath = file;
  }

  /**
   * @return the URL for the repository
   */
  public URIish getRepositoryFetchURL() {
    return myRepositoryFetchURL;
  }

  /**
   * @return the branch name
   */
  public String getRef() {
    return StringUtil.isEmptyOrSpaces(myRef) ? "master" : myRef;
  }

  /**
   * @return debug information that allows identify repository operation context
   */
  public String debugInfo() {
    return " (" + getRepositoryDir() + ", " + getRepositoryFetchURL().toString() + "#" + getRef() + ")";
  }

  /**
   * @return the push URL for the repository
   */
  public URIish getRepositoryPushURL() {
    return myRepositoryPushURL;
  }

  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  public static class AuthSettings {
    private final AuthenticationMethod myAuthMethod;
    private final boolean myIgnoreKnownHosts;
    private final String myPassphrase;
    private final String myPrivateKeyFilePath;
    private final String myUserName;
    private final String myPassword;

    public AuthSettings(Map<String, String> properties) {
      myAuthMethod = readAuthMethod(properties);
      myIgnoreKnownHosts = "true".equals(properties.get(Constants.IGNORE_KNOWN_HOSTS));
      if (myAuthMethod == AuthenticationMethod.PRIVATE_KEY_FILE) {
        myPassphrase = properties.get(Constants.PASSPHRASE);
        myPrivateKeyFilePath = properties.get(Constants.PRIVATE_KEY_PATH);
      } else {
        myPassphrase = null;
        myPrivateKeyFilePath = null;
      }
      myUserName = myAuthMethod == AuthenticationMethod.ANONYMOUS ? null : properties.get(Constants.USERNAME);
      myPassword = myAuthMethod != AuthenticationMethod.PASSWORD ? null : properties.get(Constants.PASSWORD);
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

    public String getPrivateKeyFilePath() {
      return myPrivateKeyFilePath;
    }

    public URIish createAuthURI(String uri) throws VcsException {
      URIish result;
      try {
        result = new URIish(uri);
      } catch (URISyntaxException e) {
        throw new VcsException("Invalid URI: " + uri);
      }
      return createAuthURI(result);
    }

    public URIish createAuthURI(final URIish uri) {
      URIish result = uri;
      if (requiresCredentials(result)) {
        if (!StringUtil.isEmptyOrSpaces(myUserName)) {
          result = result.setUser(myUserName);
        }
        if (!StringUtil.isEmpty(myPassword)) {
          result = result.setPass(myPassword);
        }
      }
      return result;
    }

    private boolean requiresCredentials(URIish result) {
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
            if (myAuthMethod != AuthenticationMethod.ANONYMOUS && i instanceof CredentialItem.Username) {
              continue;
            } else if (myAuthMethod == AuthenticationMethod.PASSWORD && i instanceof CredentialItem.Password) {
              continue;
            } else {
              return false;
            }
          }
          return true;
        }

        @Override
        public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
          for (CredentialItem i : items) {
            if (myAuthMethod != AuthenticationMethod.ANONYMOUS && i instanceof CredentialItem.Username) {
              ((CredentialItem.Username) i).setValue(myUserName);
            } else if (myAuthMethod == AuthenticationMethod.PASSWORD && i instanceof CredentialItem.Password) {
              ((CredentialItem.Password) i).setValue(myPassword.toCharArray());
            } else {
              throw new UnsupportedCredentialItem(uri, i.getPromptText());
            }
          }
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

  /**
   * The style for user names
   */
  enum UserNameStyle {
    /**
     * Name (John Smith)
     */
    NAME,
    /**
     * User id based on email (jsmith)
     */
    USERID,
    /**
     * Email (jsmith@example.org)
     */
    EMAIL,
    /**
     * Name and Email (John Smith &ltjsmith@example.org&gt)
     */
    FULL
  }

  private PersonIdent parseIdent() {
    int emailStartIdx = myUsernameForTags.indexOf("<");
    if (emailStartIdx == -1)
      return new PersonIdent(myUsernameForTags, "");
    int emailEndIdx = myUsernameForTags.lastIndexOf(">");
    if (emailEndIdx < emailStartIdx)
      return new PersonIdent(myUsernameForTags, "");
    String username = myUsernameForTags.substring(0, emailStartIdx).trim();
    String email = myUsernameForTags.substring(emailStartIdx + 1, emailEndIdx);
    return new PersonIdent(username, email);
  }
}
