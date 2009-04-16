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
  private String repositoryURL;
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
   * The constructor from the root object
   *
   * @param root the root
   * @throws VcsException in case of incorrect configuration
   */
  public Settings(VcsRoot root) throws VcsException {
    final String p = root.getProperty(Constants.PATH);
    repositoryPath = p == null ? null : new File(p);
    branch = root.getProperty(Constants.BRANCH_NAME);
    String username = root.getProperty(Constants.USERNAME);
    String password = root.getProperty(Constants.PASSWORD);
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
    publicURL = uri.toString();
    repositoryURL = uri.toPrivateString();
    final String style = root.getProperty(Constants.USERNAME_STYLE);
    usernameStyle = style == null ? UserNameStyle.USERID : Enum.valueOf(UserNameStyle.class, style);
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
  public String getRepositoryURL() {
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
