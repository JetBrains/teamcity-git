/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;

/**
 * Agent Git plugin settings class
 */
public class Settings {
  /**
   * The path to the git command
   */
  private final String gitCommandPath;
  /**
   * Local repository directory
   */
  private final File localRepositoryDir;
  /**
   * The fetch URL
   */
  private final String fetchUrl;
  /**
   * The push URL
   */
  private final String pushUrl;
  /**
   * The branch name
   */
  private final String branch;
  /**
   * True, if root properties are available
   */
  private final boolean isRootAvailable;
  /**
   * The initial depth of agent history
   */
  private final Integer agentHistoryDepth;
  /**
   * The name of the root
   */
  private final String rootName;
  /**
   * The clean policy for the agent
   */
  private final AgentCleanPolicy cleanPolicy;
  /**
   * The policy for cleaning files
   */
  private final AgentCleanFilesPolicy cleanFilesPolicy;
  /**
   * The passphrase
   */
  private final String passphrase;
  /**
   * The private key file (used for {@link AuthenticationMethod#PRIVATE_KEY_FILE})
   */
  private final String privateKeyFile;
  /**
   * The password
   */
  private final String password;
  /**
   * The selected authentication method
   */
  private final AuthenticationMethod authenticationMethod;
  /**
   * If true, known hosts are ignored
   */
  private final boolean ignoreKnownHosts;
  /**
   * Submodule checkout policy
   */
  private final SubmodulesCheckoutPolicy submodulePolicy;

  /**
   * The constructor
   *
   * @param gitCommandPath     the path to the git
   * @param localRepositoryDir the local directory
   * @param root               the VCS root to get settings from
   * @throws VcsException if there is a configuration problem
   */
  public Settings(String gitCommandPath, File localRepositoryDir, VcsRoot root) throws VcsException {
    this.gitCommandPath = gitCommandPath;
    this.localRepositoryDir = localRepositoryDir;
    if (root != null) {
      isRootAvailable = true;
      this.rootName = root.getName();
      this.fetchUrl = root.getProperty(Constants.FETCH_URL);
      if (fetchUrl == null) {
        throw new VcsException("The fetch url is not specified for vcs root " + root.getName());
      }
      String b = root.getProperty(Constants.BRANCH_NAME);
      this.branch = b == null ? "master" : b;
      String push = root.getProperty(Constants.PUSH_URL);
      this.pushUrl = push == null || push.length() == 0 ? null : push;
      // FIX it when it is implemented in a more sane way
      this.agentHistoryDepth = null;
      String clean = root.getProperty(Constants.AGENT_CLEAN_POLICY);
      this.cleanPolicy = clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
      String cleanFiles = root.getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
      this.cleanFilesPolicy = cleanFiles == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(cleanFiles);
      final String authMethod = root.getProperty(Constants.AUTH_METHOD);
      authenticationMethod = authMethod == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, authMethod);
      String userName = authenticationMethod == AuthenticationMethod.ANONYMOUS ? null : root.getProperty(Constants.USERNAME);
      password = authenticationMethod != AuthenticationMethod.PASSWORD ? null : root.getProperty(Constants.PASSWORD);
      ignoreKnownHosts = "true".equals(root.getProperty(Constants.IGNORE_KNOWN_HOSTS));
      String submoduleCheckout = root.getProperty(Constants.SUBMODULES_CHECKOUT);
      submodulePolicy =
        submoduleCheckout != null ? Enum.valueOf(SubmodulesCheckoutPolicy.class, submoduleCheckout) : SubmodulesCheckoutPolicy.IGNORE;
      if (authenticationMethod == AuthenticationMethod.PRIVATE_KEY_FILE) {
        passphrase = root.getProperty(Constants.PASSPHRASE);
        privateKeyFile = root.getProperty(Constants.PRIVATE_KEY_PATH);
      } else {
        passphrase = null;
        privateKeyFile = null;
      }
    } else {
      isRootAvailable = false;
      this.rootName = null;
      this.fetchUrl = null;
      this.pushUrl = null;
      this.branch = null;
      this.agentHistoryDepth = null;
      this.cleanPolicy = null;
      this.cleanFilesPolicy = null;
      this.passphrase = null;
      this.submodulePolicy = null;
      this.password = null;
      this.privateKeyFile = null;
      this.authenticationMethod = null;
      this.ignoreKnownHosts = false;
    }
  }

  /**
   * @return the local repository directory
   */
  public File getLocalRepositoryDir() {
    return localRepositoryDir;
  }

  /**
   * @return the path to the command line git
   */
  public String getGitCommandPath() {
    return gitCommandPath;
  }

  /**
   * @return the fetch URL for VCS root
   */
  public String getFetchUrl() {
    ensureRootPropertiesAvailable();
    return fetchUrl;
  }

  /**
   * Ensure that root properties are available
   */
  private void ensureRootPropertiesAvailable() {
    if (!isRootAvailable) {
      throw new IllegalStateException("Root properties are not available in this context");
    }
  }

  /**
   * @return the branch name
   */
  public String getBranch() {
    ensureRootPropertiesAvailable();
    return branch;
  }


  /**
   * @return the push URL for repository
   */
  public String getPushUrl() {
    ensureRootPropertiesAvailable();
    return pushUrl;
  }

  /**
   * @return debug information
   */
  public String debugInfo() {
    return "(" + rootName + ", " + localRepositoryDir + ")";
  }

  /**
   * @return the depth of history on the agent
   */
  public Integer getAgentHistoryDepth() {
    ensureRootPropertiesAvailable();
    return agentHistoryDepth;
  }

  /**
   * @return the clean policy for the agent
   */
  public AgentCleanPolicy getCleanPolicy() {
    ensureRootPropertiesAvailable();
    return cleanPolicy;
  }

  /**
   * @return specifies which files should be cleaned after checkout
   */
  public AgentCleanFilesPolicy getCleanFilesPolicy() {
    return cleanFilesPolicy;
  }

  /**
   * @return the passphrase for the private key
   */
  public String getPassphrase() {
    return passphrase;
  }

  /**
   * @return the password for username/password authentication method
   */
  public String getPassword() {
    return password;
  }

  public AuthenticationMethod getAuthenticationMethod() {
    return authenticationMethod;
  }

  /**
   * @return true if submodules should be checked out
   */
  public boolean areSubmodulesCheckedOut() {
    return submodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT;
  }

  /**
   * @return true, if known host check should be skipped
   */
  public boolean isIgnoreKnownHosts() {
    return ignoreKnownHosts;
  }

  public String getPrivateKeyFile() {
    return privateKeyFile;
  }
}
