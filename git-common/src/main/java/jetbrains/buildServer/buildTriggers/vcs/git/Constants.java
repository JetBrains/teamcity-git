

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

  public static final String COMPLETE_BRANCH_SPEC = "+:refs/heads/*";

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
   * (Refreshable) token id
   */
  public static final String TOKEN_ID = "tokenId";

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
  public static final String TEAMCITY_AGENT_GIT_VERSION = "TEAMCITY_GIT_VERSION";
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
  public static final String GC_DUMP_FILE = "GC_DUMP_FILE";

  public static final String USERNAME_FOR_TAGS = "userForTags";

  public static final String SERVER_SIDE_AUTO_CRLF = "serverSideAutoCrlf";

  public static final String REPORT_TAG_REVISIONS = "reportTagRevisions";

  //path to internal properties to use in Fetcher
  public static final String FETCHER_INTERNAL_PROPERTIES_FILE = "fetcherInternalPropertiesFile";

  public static final String GIT_TRUST_STORE_PROVIDER = "gitTrustStoreProvider";

  /**
   * A prefix for build parameter with vcs branch name of git root
   * @deprecated use teamcity.build.vcs.branch.ROOT_EXT_ID instead
   */
  public static final String GIT_ROOT_BUILD_BRANCH_PREFIX = "teamcity.git.build.vcs.branch.";
  String USE_DEPRECATED_GIT_BRANCH_PARAMETERS_INTERNAL_PROP = "teamcity.git.deprecatedGitBranchParameters.enabled";

  String IGNORE_SUBMODULE_ERRORS = "teamcity.git.changesCollection.ignoreSubmoduleErrors";
  String COLLECT_BROKEN_SUBMODULES_INFO = "teamcity.git.changesCollection.collectBrokenSubmodulesInfo";

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
  String CHECKOUT_POLICY = "useAlternates";
  String CUSTOM_GIT_CONFIG = "customGitConfig";

  String SHOW_CUSTOM_CLONE_PATH = "teamcity.git.showCustomClonePath";
  String CUSTOM_CLONE_PATH_ENABLED = "teamcity.git.customClonePathEnabled";
  String NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND = "teamcity.git.native.retryIfRemoteRefNotFound";
  String AMAZON_HOSTS = "teamcity.git.amazonHosts";

  String GIT_HTTP_CRED_PREFIX = "teamcity.git.https.credentials";

  String NON_RECURSIVE_SUBMODULES_ENABLE = "teamcity.git.submodules.nonrecursive.enable";


  String WARN_FILE_URL = "teamcity.git.health.warnFileUrl";

  /**
   * @since 2026.1
   * @see jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig#isAllowFileUrl
   */
  String ALLOW_FILE_URL = "teamcity.git.allowFileUrl";

  /**
   * If set, native ssh will fallback to default config (~/.ssh/config and /etc/ssh/ssh_config) if the specified key is invalid
   *
   * @since 2026.1
   */
  String SSH_FALLBACK_TO_DEFAULT_CONFIG = "teamcity.git.ssh.fallbackToDefaultConfig";

  /**
   * Timeout in milliseconds for a token to be considered as recently retrieved.
   * @since 2026.1
   * @see AuthSettingsImpl#isFreshToken
   */
  String FRESH_TOKEN_TIMEOUT_MILLIS = "teamcity.git.freshTokenTimeoutMillis";

  /**
   * Max retry attempts on a "not found" error for fresh tokens.
   * <br>
   * In case it is not enough to have a single retry attempt on a fresh token (with a tiny default delay),
   * it would make sense to retry as long as the token is considered fresh by the {@link Constants#FRESH_TOKEN_TIMEOUT_MILLIS} property.
   * However, we must have a hard limit on those attempts to ensure we're not retrying indefinitely.
   *
   * @since 2026.1
   */
  int FRESH_TOKEN_MAX_RETRY_ATTEMPTS = 3;

  /**
   * Max length of the VCS error message (stdout or stderr). Introduced to prevent OOM on large error messages.
   * @since 2026.1
   */
  String GIT_MAX_LENGTH_OF_VCS_ERROR_MESSAGE = "teamcity.git.error.message.maxLength";


  /**
   * `git remote prune` clears local refs that no longer exist on the remote (branch was deleted or renamed).
   * This command executes ls-remote under the hood because it needs to receive the list of remote refs,
   * so execution before every fetch is resource-consuming (legacy behavior). This feature toggle re-enables that legacy behavior.
   * Otherwise, prune makes sense before garbage collection, and when the fetch command fails with "some local refs could not be updated".
   * This error is raised if a namespace conflict occurs: e.g. there was `branch` locally and remotely, then it was deleted remotely and remote
   * `branch/X` was created. So, the local `branch` should be deleted.
   * @since 2026.2
   */
  String GIT_EXECUTE_PRUNE_BEFORE_FETCH = "teamcity.git.fetch.prune.enabled";


  /**
   * Enables automatic recovery from corrupted commit-graph files.
   * <p>
   * Git uses commit-graph files to speed up operations by caching commit metadata. However, these files can become
   * corrupted due to incomplete maintenance operations or other issues. When this happens, fetch operations may fail
   * with an error message: "unable to find all commit-graph files".
   * <p>
   * When this property is enabled (default), TeamCity will automatically attempt to fix the corrupted commit-graph
   * by executing {@code git commit-graph write --reachable --split=replace}. This command rebuilds the commit-graph
   * from scratch, resolving the corruption and allowing the fetch operation to proceed.
   * <p>
   * It is recommended to keep this feature enabled to ensure repository operations continue smoothly even when
   * commit-graph corruption occurs.
   * TW-100479 TeamCity builds sporadically fail to perform checkout with Git 2.54.0: unable to find all commit-graph files
   * @since 2026.2
   */
  String GIT_REFRESH_COMMIT_GRAPH_IF_CORRUPTED = "teamcity.git.commit.graph.refresh.enable";
}