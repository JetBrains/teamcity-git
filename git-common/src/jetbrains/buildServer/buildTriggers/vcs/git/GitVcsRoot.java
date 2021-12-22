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
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Git Vcs Settings
 */
public class GitVcsRoot {

  protected final MirrorManager myMirrorManager;
  private final VcsRoot myDelegate;
  private final CommonURIish myRepositoryFetchURL;
  private final CommonURIish myRepositoryFetchURLNoFixErrors;
  private final CommonURIish myRepositoryPushURL;
  private final CommonURIish myRepositoryPushURLNoFixErrors;
  private final String myRef;
  private final UserNameStyle myUsernameStyle;
  private final SubmodulesCheckoutPolicy mySubmodulePolicy;
  private final AuthSettings myAuthSettings;
  private final String myUsernameForTags;
  private final String myBranchSpec;
  private final boolean myAutoCrlf;
  private boolean myReportTags;
  private final boolean myIgnoreMissingDefaultBranch;
  private final boolean myIncludeCommitInfoSubmodules;
  private File myCustomRepositoryDir;
  private final AgentCheckoutPolicy myCheckoutPolicy;
  private final boolean myIncludeContentHashes;
  private final URIishHelper myURIishHelper;

  public GitVcsRoot(@NotNull final MirrorManager mirrorManager, @NotNull final VcsRoot root, @NotNull URIishHelper urIishHelper) throws VcsException {
    this(mirrorManager, root, root.getProperty(Constants.BRANCH_NAME), urIishHelper);
  }

  public GitVcsRoot(@NotNull MirrorManager mirrorManager, @NotNull VcsRoot root, @Nullable String ref, @NotNull URIishHelper urIishHelper) throws VcsException {
    myMirrorManager = mirrorManager;
    myDelegate = root;
    myCustomRepositoryDir = getPath();
    myRef = ref;
    myURIishHelper = urIishHelper;
    myUsernameStyle = readUserNameStyle();
    mySubmodulePolicy = readSubmodulesPolicy();
    myAuthSettings = createAuthSettings(urIishHelper);
    String rawFetchUrl = getProperty(Constants.FETCH_URL);
    if (rawFetchUrl.contains("\n") || rawFetchUrl.contains("\r"))
      throw new VcsException("Newline in fetch url '" + rawFetchUrl + "'");
    myRepositoryFetchURL = myURIishHelper.createAuthURI(myAuthSettings, rawFetchUrl);
    myRepositoryFetchURLNoFixErrors = myURIishHelper.createAuthURI(myAuthSettings, rawFetchUrl, false);
    String pushUrl = getProperty(Constants.PUSH_URL);
    if (pushUrl != null && (pushUrl.contains("\n") || pushUrl.contains("\r")))
      throw new VcsException("Newline in push url '" + pushUrl + "'");
    myRepositoryPushURL = StringUtil.isEmpty(pushUrl) ? myRepositoryFetchURL : myURIishHelper.createAuthURI(myAuthSettings, pushUrl);
    myRepositoryPushURLNoFixErrors = StringUtil.isEmpty(pushUrl) ? myRepositoryFetchURLNoFixErrors : myURIishHelper.createAuthURI(myAuthSettings, pushUrl, false);
    myUsernameForTags = getProperty(Constants.USERNAME_FOR_TAGS);
    myBranchSpec = getProperty(Constants.BRANCH_SPEC);
    myAutoCrlf = Boolean.valueOf(getProperty(Constants.SERVER_SIDE_AUTO_CRLF, "false"));
    myIncludeContentHashes = Boolean.valueOf(getProperty(Constants.INCLUDE_CONTENT_HASHES, "false"));
    myReportTags = Boolean.valueOf(getProperty(Constants.REPORT_TAG_REVISIONS, "false"));
    myIgnoreMissingDefaultBranch = Boolean.valueOf(getProperty(Constants.IGNORE_MISSING_DEFAULT_BRANCH, "false"));
    myIncludeCommitInfoSubmodules = Boolean.valueOf(getProperty(Constants.INCLUDE_COMMIT_INFO_SUBMODULES, "false"));
    myCheckoutPolicy = readCheckoutPolicy();
  }

  private AuthSettings createAuthSettings(@NotNull URIishHelper urIishHelper) {
    return new AuthSettingsImpl(this, urIishHelper, null);
  }

  public GitVcsRoot getRootForBranch(@NotNull String branch) throws VcsException {
    return new GitVcsRoot(myMirrorManager, myDelegate, branch, myURIishHelper);
  }

  @Nullable
  public String getBranchSpec() {
    return myBranchSpec;
  }

  @NotNull
  public AgentCheckoutPolicy getAgentCheckoutPolicy() {
    return myCheckoutPolicy;
  }

  @Nullable
  private File getPath() {
    if (!TeamCityProperties.getBoolean(Constants.CUSTOM_CLONE_PATH_ENABLED))
      return null;
    String path = getProperty(Constants.PATH);
    return path == null ? null : new File(path);
  }

  @NotNull
  private AgentCheckoutPolicy readCheckoutPolicy() {
    String useAgentMirrors = getProperty(Constants.CHECKOUT_POLICY);
    if (StringUtil.isEmpty(useAgentMirrors) || "false".equalsIgnoreCase(useAgentMirrors)) return AgentCheckoutPolicy.NO_MIRRORS;
    if ("true".equalsIgnoreCase(useAgentMirrors)) return AgentCheckoutPolicy.USE_MIRRORS;
    try {
      return Enum.valueOf(AgentCheckoutPolicy.class, useAgentMirrors);
    } catch (IllegalArgumentException e) {
      final AgentCheckoutPolicy fallback = AgentCheckoutPolicy.NO_MIRRORS;
      Loggers.VCS.warn(Constants.CHECKOUT_POLICY + " property has unexpected value \"" + useAgentMirrors + "\" for " + LogUtil.describe(myDelegate) + ", will use " + fallback);
      return fallback;
    }
  }

  private UserNameStyle readUserNameStyle() {
    final String style = getProperty(Constants.USERNAME_STYLE);
    if (style == null) {
      return UserNameStyle.USERID;
    } else {
      return Enum.valueOf(UserNameStyle.class, style);
    }
  }

  private SubmodulesCheckoutPolicy readSubmodulesPolicy() {
    final String submoduleCheckout = getProperty(Constants.SUBMODULES_CHECKOUT);
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
    if (myCustomRepositoryDir != null) {
      return myCustomRepositoryDir.isAbsolute() ?
             myCustomRepositoryDir :
             new File(myMirrorManager.getBaseMirrorsDir(), myCustomRepositoryDir.getPath());
    }
    return myMirrorManager.getMirrorDir(fetchUrl);
  }

  public boolean isAutoCrlf() {
    return myAutoCrlf;
  }

  public boolean isIncludeContentHashes() {
    return myIncludeContentHashes;
  }

  public boolean isReportTags() {
    return myReportTags;
  }

  public void setReportTags(final boolean reportTags) {
    myReportTags = reportTags;
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setCustomRepositoryDir(File file) {
    myCustomRepositoryDir = file;
  }

  /**
   * @return the URL for the repository
   */
  public CommonURIish getRepositoryFetchURL() {
    return myRepositoryFetchURL;
  }

  public CommonURIish getRepositoryFetchURLNoFixedErrors() {
    return myRepositoryFetchURLNoFixErrors;
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
  public CommonURIish getRepositoryPushURL() {
    return myRepositoryPushURL;
  }

  public CommonURIish getRepositoryPushURLNoFixedErrors() {
    return myRepositoryPushURLNoFixErrors;
  }

  public String getUsernameForTags() {
    return myUsernameForTags;
  }

  @NotNull
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
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

  public VcsRoot getOriginalRoot() {
    return myDelegate;
  }

  @NotNull
  public String getVcsName() {
    return myDelegate.getVcsName();
  }

  public String getProperty(@NotNull String propertyName) {
    return myDelegate.getProperty(propertyName);
  }

  public String getProperty(@NotNull String propertyName, String defaultValue) {
    return myDelegate.getProperty(propertyName, defaultValue);
  }

  @NotNull
  public Map<String, String> getProperties() {
    return myDelegate.getProperties();
  }

  @NotNull
  public String getName() {
    return myDelegate.getName();
  }

  public long getId() {
    return myDelegate.getId();
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }

  @NotNull
  public String describe(final boolean verbose) {
    return myDelegate.describe(verbose);
  }

  public boolean isOnGithub() {
    return "github.com".equals(myRepositoryFetchURL.getHost());
  }

  public boolean isSsh() {
    return myRepositoryFetchURL.getScheme() == null ||
           "ssh".equals(myRepositoryFetchURL.getScheme());
  }

  public boolean isHttp() {
    return "http".equals(myRepositoryFetchURL.getScheme()) ||
           "https".equals(myRepositoryFetchURL.getScheme());
  }

  public boolean isIgnoreMissingDefaultBranch() {
    return myIgnoreMissingDefaultBranch;
  }

  public boolean isIncludeCommitInfoSubmodules() {
    return myIncludeCommitInfoSubmodules;
  }
}
