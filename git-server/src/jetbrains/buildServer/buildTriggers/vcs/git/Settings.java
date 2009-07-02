/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Git Vcs Settings
 */
public class Settings {
  /**
   * The url for the repository
   */
  private URIish repositoryURL;
  /**
   * The public URL
   */
  private String publicURL;
  /**
   * The current branch
   */
  private String branch;
  /**
   * The repository path
   */
  private File repositoryPath;
  /**
   * The style for user name
   */
  private UserNameStyle usernameStyle;
  /**
   * The authentication method
   */
  private AuthenticationMethod authenticationMethod;
  /**
   * Submodule checkout policy
   */
  private SubmodulesCheckoutPolicy submodulePolicy;
  /**
   * The passphrase (used for {@link AuthenticationMethod#PRIVATE_KEY_FILE})
   */
  private String passprase;
  /**
   * The private key file (used for {@link AuthenticationMethod#PRIVATE_KEY_FILE})
   */
  private String privateKeyFile;
  /**
   * If true, known hosts are ignored
   */
  private boolean ignoreKnownHosts;

  /**
   * The constructor from the root object
   *
   * @param root the root
   * @throws VcsException in case of incorrect configuration
   */
  public Settings(VcsRoot root) throws VcsException {
    final String p = root.getProperty(Constants.PATH);
    repositoryPath = p == null ? null : new File(p);
    branch = root.getProperty(Constants.BRANCH_NAME);
    final String style = root.getProperty(Constants.USERNAME_STYLE);
    usernameStyle = style == null ? UserNameStyle.USERID : Enum.valueOf(UserNameStyle.class, style);
    String submoduleCheckout = root.getProperty(Constants.SUBMODULES_CHECKOUT);
    submodulePolicy =
      submoduleCheckout != null ? Enum.valueOf(SubmodulesCheckoutPolicy.class, submoduleCheckout) : SubmodulesCheckoutPolicy.IGNORE;
    final String authMethod = root.getProperty(Constants.AUTH_METHOD);
    authenticationMethod = authMethod == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, authMethod);
    String username = authenticationMethod == AuthenticationMethod.ANONYMOUS ? null : root.getProperty(Constants.USERNAME);
    String password = authenticationMethod != AuthenticationMethod.PASSWORD ? null : root.getProperty(Constants.PASSWORD);
    ignoreKnownHosts = "true".equals(root.getProperty(Constants.IGNORE_KNOWN_HOSTS));
    final String remote = root.getProperty(Constants.URL);
    URIish uri;
    try {
      uri = new URIish(remote);
    } catch (URISyntaxException e) {
      throw new VcsException("Invalid URI: " + remote);
    }
    if (!StringUtil.isEmptyOrSpaces(username)) {
      uri = uri.setUser(username);
    }
    if (!StringUtil.isEmpty(password)) {
      uri = uri.setPass(password);
    }
    if (authenticationMethod == AuthenticationMethod.PRIVATE_KEY_FILE) {
      passprase = root.getProperty(Constants.PASSPHRASE);
      privateKeyFile = root.getProperty(Constants.PRIVATE_KEY_PATH);
    }
    publicURL = uri.toString();
    repositoryURL = uri;
  }


  /**
   * @return true if submodules should be checked out
   */
  public boolean areSubmodulesCheckedOut() {
    return submodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT;
  }

  /**
   * @return username sytle
   */
  public UserNameStyle getUsernameStyle() {
    return usernameStyle;
  }

  /**
   * @return the URL with pasword removed
   */
  public String getPublicURL() {
    return publicURL;
  }

  /**
   * @return the local repository path
   */
  public File getRepositoryPath() {
    return repositoryPath;
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setRepositoryPath(File file) {
    repositoryPath = file;
  }

  /**
   * @return the URL for the repository
   */
  public URIish getRepositoryURL() {
    return repositoryURL;
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
    return " (" + getRepositoryPath() + ", " + getPublicURL() + "#" + getBranch() + ")";
  }

  /**
   * Authentication method to use
   *
   * @return the authentication method
   */
  public AuthenticationMethod getAuthenticationMethod() {
    return authenticationMethod;
  }


  /**
   * @return the passphrase for private key
   */
  public String getPassprase() {
    return passprase;
  }

  /**
   * @return the path to private key file
   */
  public String getPrivateKeyFile() {
    return privateKeyFile;
  }

  /**
   * @return true if the result of checking in known hosts database should be ignored
   */
  public boolean isKnownHostsIgnored() {
    return ignoreKnownHosts;
  }

  /**
   * Authentication method
   */
  enum AuthenticationMethod {
    /**
     * Anonymous access (or password is a part of URL)
     */
    ANONYMOUS,
    /**
     * The default SSH private key
     */
    PRIVATE_KEY_DEFAULT,
    /**
     * The private key is specified in the file
     */
    PRIVATE_KEY_FILE,
    /**
     * The password is used
     */
    PASSWORD
  }

  /**
   * Submodule checkout policy
   */
  enum SubmodulesCheckoutPolicy {
    /**
     * Ignore submodules
     */
    IGNORE,
    /**
     * Checkout submodules
     */
    CHECKOUT,
  }

  /**
   * The stype for user names
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
