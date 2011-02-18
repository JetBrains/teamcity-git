/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Git Vcs Settings
 */
public class Settings {

  final private URIish repositoryFetchURL;
  final private URIish repositoryPushURL;
  final private String branch;
  final private UserNameStyle usernameStyle;
  final private SubmodulesCheckoutPolicy submodulePolicy;
  final private String cachesDirectory;
  final private AuthSettings myAuthSettings;
  private File userDefinedRepositoryPath;

  public Settings(VcsRoot root) throws VcsException {
    this(root, null);
  }

  public Settings(VcsRoot root, String cacheDir) throws VcsException {
    cachesDirectory = cacheDir;
    userDefinedRepositoryPath = readPath(root);
    branch = root.getProperty(Constants.BRANCH_NAME);
    usernameStyle = readUserNameStyle(root);
    submodulePolicy = readSubmodulesPolicy(root);
    myAuthSettings = new AuthSettings(root.getProperties());
    repositoryFetchURL = myAuthSettings.createAuthURI(root.getProperty(Constants.FETCH_URL));
    final String pushUrl = root.getProperty(Constants.PUSH_URL);
    repositoryPushURL = StringUtil.isEmpty(pushUrl) ? repositoryFetchURL : myAuthSettings.createAuthURI(pushUrl);
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
    return submodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT ||
           submodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS ||
           submodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT ||
           submodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS;
  }

  public SubmodulesCheckoutPolicy getSubmodulesCheckoutPolicy() {
    return submodulePolicy;
  }

  /**
   * @return username style
   */
  public UserNameStyle getUsernameStyle() {
    return usernameStyle;
  }

  /**
   * @return the local repository path
   */
  public File getRepositoryPath() {
    if (userDefinedRepositoryPath == null) {
      return getPathForUrl(getRepositoryFetchURL().toString());
    } else {
      return userDefinedRepositoryPath;
    }
  }

  public static File getRepositoryPath(File cacheDir, VcsRoot root) throws VcsException {
    File userDefinedPath = readPath(root);
    if (userDefinedPath == null) {
      AuthSettings auth = new AuthSettings(root.getProperties());
      URIish fetchUrl = auth.createAuthURI(root.getProperty(Constants.FETCH_URL));
      return getPathForUrl(cacheDir, fetchUrl.toString());
    } else {
      return userDefinedPath;
    }
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setUserDefinedRepositoryPath(File file) {
    userDefinedRepositoryPath = file;
  }

  /**
   * @return the URL for the repository
   */
  public URIish getRepositoryFetchURL() {
    return repositoryFetchURL;
  }

  /**
   * @return the branch name
   */
  public String getBranch() {
    return branch == null || branch.length() == 0 ? "master" : branch;
  }

  /**
   * @return debug information that allows identify repository operation context
   */
  public String debugInfo() {
    return " (" + getRepositoryPath() + ", " + getRepositoryFetchURL().toString() + "#" + getBranch() + ")";
  }

  /**
   * Get server paths for the URL
   *
   * @param url the URL to get path for
   * @return the internal directory name for the URL
   */
  public File getPathForUrl(String url) {
    return getPathForUrl(new File(cachesDirectory), url);
  }

  public static File getPathForUrl(File cacheDir, String url) {
    // TODO the directory needs to be cleaned up
    // TODO consider using a better hash in order to reduce a chance for conflict
    String name = String.format("git-%08X.git", url.hashCode() & 0xFFFFFFFFL);
    return new File(cacheDir, name);
  }

  /**
   * @return the push URL for the repository
   */
  public URIish getRepositoryPushURL() {
    return repositoryPushURL;
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
      if (!"git".equals(result.getScheme())) {
        if (!StringUtil.isEmptyOrSpaces(myUserName)) {
          result = result.setUser(myUserName);
        }
        if (!StringUtil.isEmpty(myPassword)) {
          result = result.setPass(myPassword);
        }
      }
      return result;
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
}
