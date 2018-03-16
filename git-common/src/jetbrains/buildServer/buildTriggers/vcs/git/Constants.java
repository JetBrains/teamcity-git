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

import jetbrains.buildServer.vcs.VcsRoot;

/**
 * The configuration constants
 */
public interface Constants {
  /**
   * The fetch URL property
   */
  public static final String FETCH_URL = "url";
  /**
   * The push URL property
   */
  public static final String PUSH_URL = "push_url";
  /**
   * The path property
   */
  public static final String PATH = "path";
  /**
   * The branch name property
   */
  public static final String BRANCH_NAME = "branch";

  public static final String BRANCH_SPEC = "teamcity:branchSpec";

  /**
   * The branch name property
   */
  public static final String SUBMODULES_CHECKOUT = "submoduleCheckout";
  /**
   * The user name property
   */
  public static final String AUTH_METHOD = "authMethod";
  /**
   * The user name property
   */
  public static final String USERNAME = "username";
  /**
   * The user name property
   */
  public static final String PRIVATE_KEY_PATH = "privateKeyPath";
  /**
   * The password property name
   */
  public static final String PASSWORD = VcsRoot.SECURE_PROPERTY_PREFIX + "password";
  /**
   * The password property name
   */
  public static final String PASSPHRASE = VcsRoot.SECURE_PROPERTY_PREFIX + "passphrase";
  /**
   * The vcs name
   */
  public static final String VCS_NAME = "jetbrains.git";
  /**
   * The user name property
   */
  public static final String USERNAME_STYLE = "usernameStyle";
  /**
   * The ignore known hosts property
   */
  public static final String IGNORE_KNOWN_HOSTS = "ignoreKnownHosts";
  /**
   * The property that specifies when working tree should be cleaned on agent
   */
  public static final String AGENT_CLEAN_POLICY = "agentCleanPolicy";
  /**
   * The property that specifies what part of working tree should be cleaned
   */
  public static final String AGENT_CLEAN_FILES_POLICY = "agentCleanFilesPolicy";

  public static final String AGENT_GIT_PATH = "agentGitPath";
  public static final String TEAMCITY_AGENT_GIT_PATH = "TEAMCITY_GIT_PATH";
  public static final String TEAMCITY_AGENT_GIT_PATH_FULL_NAME = "env.TEAMCITY_GIT_PATH";
  /**
   * Path to bare repository dir, used in communication with Fetcher
   */
  public static final String REPOSITORY_DIR_PROPERTY_NAME = "REPOSITORY_DIR";
  /**
   * Refspec to fetch, used in communication with Fetcher
   */
  public static final String REFSPEC = "REFSPEC";
  public static final String VCS_DEBUG_ENABLED = "VCS_DEBUG_ENABLED";
  public static final String THREAD_DUMP_FILE = "THREAD_DUMP_FILE";

  public static final String USERNAME_FOR_TAGS = "userForTags";

  public static final String SERVER_SIDE_AUTO_CRLF = "serverSideAutoCrlf";

  public static final String REPORT_TAG_REVISIONS = "reportTagRevisions";

  //path to internal properties to use in Fetcher
  public static final String FETCHER_INTERNAL_PROPERTIES_FILE = "fetcherInternalPropertiesFile";

  public static final String GIT_TRUST_STORE_PROVIDER = "gitTrustStoreProvider";

  /**
   * A prefix for build parameter with vcs branch name of git root
   */
  public static final String GIT_ROOT_BUILD_BRANCH_PREFIX = "teamcity.git.build.vcs.branch.";

  String RECORD_SEPARATOR = new String(new char[]{30});

  String IGNORE_MISSING_DEFAULT_BRANCH = "IGNORE_MISSING_DEFAULT_BRANCH";
  String INCLUDE_COMMIT_INFO_SUBMODULES = "INCLUDE_COMMIT_INFO_SUBMODULES";
  String INCLUDE_CONTENT_HASHES = "INCLUDE_CONTENT_HASHES";

  String PATCHER_FROM_REVISION = "patcher.fromRevision";
  String PATCHER_TO_REVISION = "patcher.toRevision";
  String PATCHER_CHECKOUT_RULES = "patcher.checkoutRules";
  String PATCHER_CACHES_DIR = "patcher.cachesDir";
  String PATCHER_PATCH_FILE = "patcher.patchFile";
  String PATCHER_UPLOADED_KEY = "patcher.uploadedKey";
  String USE_AGENT_MIRRORS = "useAlternates";

  String SHOW_CUSTOM_CLONE_PATH = "teamcity.git.showCustomClonePath";
  String CUSTOM_CLONE_PATH_ENABLED = "teamcity.git.customClonePathEnabled";
  String AMAZON_HOSTS = "teamcity.git.amazonHosts";
}
