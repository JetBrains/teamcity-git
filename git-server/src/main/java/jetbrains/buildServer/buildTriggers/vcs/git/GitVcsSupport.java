

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.jcraft.jsch.JSch;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.ChangesCollectorCache;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitApiClientFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchBuilderDispatcher;
import jetbrains.buildServer.metrics.*;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.util.CollectionsUtil.setOf;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsBulkSuitabilityChecker, BuildPatchByCheckoutRules,
             TestConnectionSupport, IncludeRuleBasedMappingProvider,
             VcsRootIdentityProvider {

  private static final Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  static final String GIT_REPOSITORY_HAS_NO_BRANCHES = "Git repository has no branches";
  static final String DEFAULT_BRANCH_REVISION_NOT_FOUND = "Cannot find revision of the default branch";

  private final GitRepoOperations myGitRepoOperations;
  private ExtensionHolder myExtensionHolder;
  private ServerMetrics myServerMetrics;
  private volatile String myDisplayName = null;
  private final ServerPluginConfig myConfig;
  private final TransportFactory myTransportFactory;
  private final RepositoryManager myRepositoryManager;
  private final GitMapFullPath myMapFullPath;
  private final CommitLoader myCommitLoader;
  private final VcsRootSshKeyManager mySshKeyManager;
  private final VcsOperationProgressProvider myProgressProvider;
  private final GitTrustStoreProvider myGitTrustStoreProvider;
  private final TestConnectionSupport myTestConnection;
  private final TokenRefresher myTokenRefresher;
  private final CheckoutRulesLatestRevisionCache myCheckoutRulesLatestRevisionCache;
  private final Collection<GitServerExtension> myExtensions = new ArrayList<GitServerExtension>();
  private Counter myCurrentStateMetric = new NoOpCounter();
  private final SSLTrustStoreProvider mySslTrustStoreProvider;
  private final ParameterFactory myParameterFactory;
  private final ChangesCollectorCache myChangesCollectorCache;

  public GitVcsSupport(@NotNull GitRepoOperations gitRepoOperations,
                       @NotNull ServerPluginConfig config,
                       @NotNull ResetCacheRegister resetCacheManager,
                       @NotNull TransportFactory transportFactory,
                       @NotNull RepositoryManager repositoryManager,
                       @NotNull GitMapFullPath mapFullPath,
                       @NotNull CommitLoader commitLoader,
                       @NotNull VcsRootSshKeyManager sshKeyManager,
                       @NotNull VcsOperationProgressProvider progressProvider,
                       @NotNull GitResetCacheHandler resetCacheHandler,
                       @NotNull ResetRevisionsCacheHandler resetRevisionsCacheHandler,
                       @NotNull TokenRefresher tokenRefresher,
                       @Nullable TestConnectionSupport customTestConnection,
                       @NotNull CheckoutRulesLatestRevisionCache checkoutRulesLatestRevisionCache,
                       @NotNull SSLTrustStoreProvider sslTrustStoreProvider,
                       @NotNull ParameterFactory parameterFactory,
                       @NotNull ChangesCollectorCache changesCollectorCache) {
    this(gitRepoOperations, config, resetCacheManager, transportFactory, repositoryManager, mapFullPath, commitLoader, sshKeyManager, progressProvider,
         resetCacheHandler, resetRevisionsCacheHandler, new GitTrustStoreProviderStatic(null), tokenRefresher, customTestConnection,
         checkoutRulesLatestRevisionCache, sslTrustStoreProvider, parameterFactory, changesCollectorCache);
  }

  public GitVcsSupport(@NotNull GitRepoOperations gitRepoOperations,
                       @NotNull ServerPluginConfig config,
                       @NotNull ResetCacheRegister resetCacheManager,
                       @NotNull TransportFactory transportFactory,
                       @NotNull RepositoryManager repositoryManager,
                       @NotNull GitMapFullPath mapFullPath,
                       @NotNull CommitLoader commitLoader,
                       @NotNull VcsRootSshKeyManager sshKeyManager,
                       @NotNull VcsOperationProgressProvider progressProvider,
                       @NotNull GitResetCacheHandler resetCacheHandler,
                       @NotNull ResetRevisionsCacheHandler resetRevisionsCacheHandler,
                       @NotNull GitTrustStoreProvider gitTrustStoreProvider,
                       @NotNull TokenRefresher tokenRefresher,
                       @Nullable TestConnectionSupport customTestConnection,
                       @NotNull CheckoutRulesLatestRevisionCache checkoutRulesLatestRevisionCache,
                       @NotNull SSLTrustStoreProvider sslTrustStoreProvider,
                       @NotNull ParameterFactory parameterFactory,
                       @NotNull ChangesCollectorCache changesCollectorCache) {
    myGitRepoOperations = gitRepoOperations;
    myConfig = config;
    myTransportFactory = transportFactory;
    myRepositoryManager = repositoryManager;
    myMapFullPath = mapFullPath;
    myCommitLoader = commitLoader;
    mySshKeyManager = sshKeyManager;
    myProgressProvider = progressProvider;
    myCheckoutRulesLatestRevisionCache = checkoutRulesLatestRevisionCache;
    setStreamFileThreshold();
    resetCacheManager.registerHandler(resetCacheHandler);
    resetCacheManager.registerHandler(resetRevisionsCacheHandler);
    myGitTrustStoreProvider = gitTrustStoreProvider;
    myTestConnection = customTestConnection == null ? this : customTestConnection;
    myTokenRefresher = tokenRefresher;
    mySslTrustStoreProvider = sslTrustStoreProvider;
    myParameterFactory = parameterFactory;
    myChangesCollectorCache = changesCollectorCache;

    JSchConfigInitializer.initJSchConfig(JSch.class);
  }

  public void setServerMetrics(@NotNull ServerMetrics serverMetrics) {
    myServerMetrics = serverMetrics;
    myCurrentStateMetric = serverMetrics.metricBuilder("vcs.git.currentState.duration")
                                        .description("Git plugin get current state duration")
                                        .dataType(MetricDataType.MILLISECONDS)
                                        .experimental(true)
                                        .buildCounter();
  }

  public void setExtensionHolder(@Nullable ExtensionHolder extensionHolder) {
    myExtensionHolder = extensionHolder;
  }

  public void addExtensions(@NotNull Collection<GitServerExtension> extensions) {
    myExtensions.addAll(extensions);
  }

  public void addExtension(@NotNull GitServerExtension extension) {
    myExtensions.add(extension);
  }

  private void setStreamFileThreshold() {
    int thresholdBytes = myConfig.getStreamFileThresholdMb() * WindowCacheConfig.MB;
    if (thresholdBytes <= 0) {
      //Config returns a threshold > 0, threshold in bytes can became non-positive due to integer overflow.
      //Since users set a value larger than the max possible one, most likely they wanted a threshold
      //to be large, so use maximum possible value.
      thresholdBytes = Integer.MAX_VALUE;
    }
    GitServerUtil.configureStreamFileThreshold(thresholdBytes);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot fromRoot,
                                               @NotNull String fromVersion,
                                               @NotNull VcsRoot toRoot,
                                               @Nullable String toVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    if (toVersion == null)
      return Collections.emptyList();
    GitVcsRoot fromGitRoot = new SGitVcsRoot(myRepositoryManager, fromRoot, new URIishHelperImpl(), myTokenRefresher);
    GitVcsRoot toGitRoot = new SGitVcsRoot(myRepositoryManager, toRoot, new URIishHelperImpl(), myTokenRefresher);
    RepositoryStateData fromState = RepositoryStateData.createVersionState(fromGitRoot.getRef(), fromVersion);
    RepositoryStateData toState = RepositoryStateData.createVersionState(toGitRoot.getRef(), toVersion);
    return getCollectChangesPolicy().collectChanges(fromRoot, fromState, toRoot, toState, checkoutRules);
  }

  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    if (currentVersion == null)
      return Collections.emptyList();
    GitVcsRoot gitRoot = new SGitVcsRoot(myRepositoryManager, root, new URIishHelperImpl(), myTokenRefresher);
    RepositoryStateData fromState = RepositoryStateData.createVersionState(gitRoot.getRef(), fromVersion);
    RepositoryStateData toState = RepositoryStateData.createVersionState(gitRoot.getRef(), currentVersion);
    return getCollectChangesPolicy().collectChanges(root, fromState, toState, checkoutRules);
  }


  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot root) throws VcsException {
    try (Stoppable stoppable = myCurrentStateMetric.startMsecsTimer()) {
      GitVcsRoot gitRoot = new SGitVcsRoot(myRepositoryManager, root, new URIishHelperImpl(), myTokenRefresher);
      return getCurrentState(gitRoot);
    }
  }

  @NotNull
  public RepositoryStateData getCurrentState(@NotNull GitVcsRoot gitRoot) throws VcsException {
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      String refInRoot = gitRoot.getRef();
      String fullRef = GitUtils.expandRef(refInRoot);
      Map<String, String> branchRevisions = new HashMap<String, String>();
      for (Ref ref : getRemoteRefs(gitRoot.getOriginalRoot()).values()) {
        if (!ref.getName().startsWith("ref"))
          continue;
        if (!gitRoot.isReportTags() && GitServerUtil.isTag(ref) && !fullRef.equals(ref.getName()))
          continue;
        branchRevisions.put(ref.getName(), GitServerUtil.getRevision(ref));
      }
      if (branchRevisions.get(fullRef) == null && !gitRoot.isIgnoreMissingDefaultBranch()) {
        if (branchRevisions.isEmpty()) {
          throw new VcsException(GIT_REPOSITORY_HAS_NO_BRANCHES);
        } else {
          throw new VcsException(DEFAULT_BRANCH_REVISION_NOT_FOUND + " '" + refInRoot + "' of vcs root '" + gitRoot.getName() + "'");
        }
      }
      return RepositoryStateData.createVersionState(fullRef, branchRevisions);
    });
  }

  public void buildPatch(@NotNull VcsRoot root,
                         @Nullable String fromVersion,
                         @NotNull String toVersion,
                         @NotNull PatchBuilder builder,
                         @NotNull CheckoutRules checkoutRules) throws VcsException {
    OperationContext context = createContext(root, "patch building");
    String fromRevision = fromVersion != null ? GitUtils.versionRevision(fromVersion) : null;
    String toRevision = GitUtils.versionRevision(toVersion);
    logBuildPatch(root, fromRevision, toRevision);
    GitVcsRoot gitRoot = context.getGitRoot();
    myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      final File trustedCertificatesDir = myGitTrustStoreProvider.getTrustedCertificatesDir();
      GitPatchBuilderDispatcher gitPatchBuilder = new GitPatchBuilderDispatcher(myConfig, mySshKeyManager, context, builder, fromRevision,
                                                                                toRevision, checkoutRules,
                                                                                trustedCertificatesDir == null ? null : trustedCertificatesDir.getAbsolutePath(),
                                                                                myConfig.isSeparateProcessForPatch() && !isNativeGitOperationEnabled(gitRoot),
                                                                                myTransportFactory);
      try {
        myCommitLoader.loadCommit(context, gitRoot, toRevision);
        if (fromRevision != null) {
          try {
            myCommitLoader.loadCommit(context, gitRoot, fromRevision);
          } catch (Throwable e) {
            LOG.warn("Could not find " + fromRevision + " in the local clone of the VCS root " + LogUtil.describe(root) + " even after fetching of all the branches, will build a full patch instead of an incremental one");
          }
        }
        gitPatchBuilder.buildPatch();
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }

  private void logBuildPatch(@NotNull VcsRoot root, @Nullable String fromRevision, @NotNull String toRevision) {
    StringBuilder msg = new StringBuilder();
    msg.append("Build");
    if (fromRevision != null)
      msg.append(" incremental");
    msg.append(" patch in VCS root ").append(LogUtil.describe(root));
    if (fromRevision != null)
      msg.append(" from revision ").append(fromRevision);
    msg.append(" to revision ").append(toRevision);
    LOG.info(msg.toString());
  }

  @NotNull
  public String getName() {
    return Constants.VCS_NAME;
  }

  @NotNull
  public String getDisplayName() {
    initDisplayNameIfRequired();
    return myDisplayName;
  }

  private void initDisplayNameIfRequired() {
    if (myDisplayName == null) {
      if (myExtensionHolder != null) {
        boolean communityPluginFound = false;
        final Collection<VcsSupportContext> vcsPlugins = myExtensionHolder.getServices(VcsSupportContext.class);
        for (VcsSupportContext plugin : vcsPlugins) {
          if (plugin.getCore().getName().equals("git")) {
            communityPluginFound = true;
          }
        }
        if (communityPluginFound) {
          myDisplayName = "Git (JetBrains plugin)";
        } else {
          myDisplayName = "Git";
        }
      } else {
        myDisplayName = "Git (JetBrains plugin)";
      }
    }
  }

  public PropertiesProcessor getVcsPropertiesProcessor() {
    return new VcsPropertiesProcessor();
  }

  @NotNull
  public String getVcsSettingsJspFilePath() {
    return "gitSettings.jsp";
  }

  @NotNull
  public String describeVcsRoot(@NotNull VcsRoot root) {
    final String branch = root.getProperty(Constants.BRANCH_NAME);
    return root.getProperty(Constants.FETCH_URL) + "#" + (branch == null ? "master" : branch);
  }

  @NotNull
  public Map<String, String> getDefaultVcsProperties() {
    // AVOID CHANGING DEFAULT VALUES - IT CAUSES ISSUES WITH COMMIT HISTORY
    final HashMap<String, String> map = new HashMap<String, String>();
    map.put(Constants.IGNORE_KNOWN_HOSTS, "true");
    map.put(Constants.AUTH_METHOD, AuthenticationMethod.ANONYMOUS.name());
    map.put(Constants.USERNAME_STYLE, GitVcsRoot.UserNameStyle.USERID.name());
    map.put(Constants.AGENT_CLEAN_POLICY, AgentCleanPolicy.ON_BRANCH_CHANGE.name());
    map.put(Constants.AGENT_CLEAN_FILES_POLICY, AgentCleanFilesPolicy.ALL_UNTRACKED.name());
    map.put(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    map.put(Constants.CHECKOUT_POLICY, AgentCheckoutPolicy.AUTO.name());
    return map;
  }

  @NotNull
  public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) {
    return GitServerUtil.displayVersion(version);
  }

  @NotNull
  public Comparator<String> getVersionComparator() {
    return GitUtils.VERSION_COMPARATOR;
  }


  public boolean sourcesUpdatePossibleIfChangesNotFound(@NotNull VcsRoot root) {
    return false;
  }

  public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
    OperationContext context = createContext(vcsRoot, "connection test");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      TestConnectionCommand command = new TestConnectionCommand(this, myTransportFactory, myRepositoryManager);
      try {
        return command.testConnection(context);
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }


  @Override
  public TestConnectionSupport getTestConnectionSupport() {
    return myTestConnection;
  }

  public OperationContext createContext(@NotNull VcsRoot root, String operation) {
    return createContext(root, operation, GitProgress.NO_OP);
  }

  public OperationContext createContext(@NotNull VcsRoot root, @NotNull String operation, @NotNull GitProgress progress) {
    return new OperationContext(myCommitLoader, myRepositoryManager, root, operation, progress, myConfig, myTokenRefresher);
  }

  @NotNull
  public LabelingSupport getLabelingSupport() {
    GitVcsSupport support = this;
    return new LabelingSupport() {
      @NotNull
      @Override
      public String label(@NotNull String label, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules) throws VcsException {
        return label(label, null, version, root, checkoutRules);
      }

      @NotNull
      @Override
      public String label(@NotNull String label, @Nullable String message, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules) throws VcsException {
        final OperationContext context = createContext(root, "labeling");
        try {
          final GitVcsRoot gitRoot = context.getGitRoot();
          RevCommit labelingCommit = myCommitLoader.findCommit(context.getRepository(), version);
          if (labelingCommit == null)
            myCommitLoader.loadCommit(context, gitRoot, version);

          String labelToPublish = label.trim().replace(' ', '_').replace('\t', '_').replace('\n', '_');

          myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
            myGitRepoOperations.tagCommand(support, gitRoot.getRepositoryFetchURL().toString()).tag(context, labelToPublish, message, version);
          });
        } catch (Exception e) {
          throw context.wrapException(e);
        } finally {
          context.close();
        }
        return label;
      }
    };
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return new GitFileContentDispatcher(this, myCommitLoader, myConfig);
  }

  @NotNull
  public GitCollectChangesPolicy getCollectChangesPolicy() {
    return new GitCollectChangesPolicy(this, myProgressProvider, myConfig, myRepositoryManager, myCheckoutRulesLatestRevisionCache,
                                       new GitApiClientFactory(mySslTrustStoreProvider), myParameterFactory, myChangesCollectorCache);
  }

  @NotNull
  public BuildPatchPolicy getBuildPatchPolicy() {
    return this;
  }

  @Override
  public VcsPersonalSupport getPersonalSupport() {
    return this;
  }

  /**
   * Expected fullPath format:
   * <p/>
   * "<git revision hash>|<repository url>|<file relative path>"
   *
   * @param rootEntry indicates the association between VCS root and build configuration
   * @param fullPath  change path from IDE patch
   * @return the mapped path
   */
  @NotNull
  public Collection<String> mapFullPath(@NotNull final VcsRootEntry rootEntry, @NotNull final String fullPath) {
    OperationContext context = createContext(rootEntry.getVcsRoot(), "map full path");
    try {
      return myRepositoryManager.runWithDisabledRemove(context.getGitRoot().getRepositoryDir(), () ->
        myMapFullPath.mapFullPath(context, rootEntry, fullPath));
    } catch (VcsException e) {
      LOG.warnAndDebugDetails("Error while mapping path for root " + LogUtil.describe(rootEntry.getVcsRoot()), e);
      return Collections.emptySet();
    } catch (Throwable t) {
      LOG.error("Error while mapping path for root " + LogUtil.describe(rootEntry.getVcsRoot()), t);
      return Collections.emptySet();
    } finally {
      context.close();
    }
  }


  @NotNull
  @Override
  public List<Boolean> checkSuitable(@NotNull List<VcsRootEntry> entries, @NotNull Collection<String> paths) {
    Set<GitMapFullPath.FullPath> fullPaths = paths.stream().map(GitMapFullPath.FullPath::new).collect(Collectors.toSet());

    Set<VcsRootEntry> vcsRootsWithPaths = findVcsRootEntriesWithPaths(fullPaths, entries);
    /* for logging */
    final Set<VcsRoot> suitableRoots = new HashSet<>();

    List<Boolean> res = new ArrayList<>();
    for (VcsRootEntry re: entries) {
      boolean hasPaths = vcsRootsWithPaths.contains(re);
      res.add(hasPaths);
      if (hasPaths) {
        suitableRoots.add(re.getVcsRoot());
      }
    }

    final long suitableCount = res.stream().filter(suitable -> suitable).count();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Found " + suitableCount + " suitable root entries in " + entries.size() + " entries for " + paths.size() + " paths: [" +
                StringUtil.join(", ", paths) + "], VCS roots: " +
                suitableRoots.stream().map(root -> root.describe(false)).collect(Collectors.joining(", ")));
    }
    return res;
  }

  @NotNull
  private Set<VcsRootEntry> findVcsRootEntriesWithPaths(@NotNull Collection<GitMapFullPath.FullPath> paths, @NotNull Collection<VcsRootEntry> rootEntries) {
    Set<VcsRootEntry> res = new HashSet<>();

    Set<String> mappedPaths = new HashSet<>();
    paths.forEach(p -> mappedPaths.addAll(p.getMappedPaths()));

    Map<Pair<File, CheckoutRules>, Boolean> cache = new HashMap<>();
    for (VcsRootEntry re: rootEntries) {
      VcsRoot root = re.getVcsRoot();
      CheckoutRules checkoutRules = re.getCheckoutRules();
      if (checkoutRules.map(mappedPaths).isEmpty()) continue;

      OperationContext context = createContext(root, "repositoryContainsPath");
      try {
        final GitVcsRoot gitRoot = context.getGitRoot();
        final File cloneDir = gitRoot.getRepositoryDir();

        // group paths with the same revisions because most likely for all of them the operation can be performed only once
        Map<Pair<String, String>, List<GitMapFullPath.FullPath>> pathsGroupedByRevisions = new HashMap<>();
        for (GitMapFullPath.FullPath path: paths) {
          pathsGroupedByRevisions.computeIfAbsent(Pair.create(path.getRevision(), path.getHintRevision()), k -> new ArrayList<>()).add(path);
        }

        boolean pathsApplicable = cache.computeIfAbsent(Pair.create(cloneDir, checkoutRules), key -> {
          try {
            for (Map.Entry<Pair<String, String>, List<GitMapFullPath.FullPath>> e: pathsGroupedByRevisions.entrySet()) {
              for (GitMapFullPath.FullPath path: e.getValue()) {
                if (checkoutRules.map(path.getMappedPaths()).isEmpty()) continue;
                final GitMapFullPath.RevisionCheckResult result = myMapFullPath.repositoryContainsPath(context, gitRoot, path);
                if (result == GitMapFullPath.RevisionCheckResult.CONTAINS_PATH) return true;

                if (result == GitMapFullPath.RevisionCheckResult.DOES_NOT_CONTAIN_REVISION) {
                  // seems the path has revisions and revisions check returned false
                  // as we grouped all the paths with the same revisions together there is no need to check other paths in the same group
                  break;
                }
              }
            }
          } catch (VcsException e) {
            LOG.warnAndDebugDetails("Error while checking suitability for root " + LogUtil.describe(root) + ", assume root is not suitable", e);
          }

          return false;
        });

        if (pathsApplicable) {
          res.add(re);
        }
      } catch (VcsException e) {
        LOG.warnAndDebugDetails("Error while checking suitability for root " + LogUtil.describe(root) + ", assume root is not suitable", e);
      } finally {
        context.close();
      }
    }

    return res;
  }

  @Override
  public boolean isAgentSideCheckoutAvailable() {
    return true;
  }


  @Override
  public UrlSupport getUrlSupport() {
    return null;
  }


  @NotNull
  public Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root, boolean useNativeGit) throws VcsException {
    return doGetRemoteRefs(root, useNativeGit);
  }

  @NotNull
  public Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    return doGetRemoteRefs(root, null);
  }

  @NotNull
  private Map<String, Ref> doGetRemoteRefs(@NotNull final VcsRoot root, @Nullable Boolean useNativeGit) throws VcsException {
    OperationContext context = createContext(root, "list remote refs");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      Repository db = context.getRepository();
      Map<String, Ref> remoteRefs = doGetRemoteRefs(db, gitRoot, useNativeGit == null
                                                                 ? myGitRepoOperations.lsRemoteCommand(gitRoot.getRepositoryFetchURL().toString())
                                                                 : myGitRepoOperations.lsRemoteCommand(useNativeGit));
      if (LOG.isDebugEnabled() && myConfig.logRemoteRefs())
        LOG.debug("Remote refs for VCS root " + LogUtil.describe(root) + ": " + remoteRefs);
      return remoteRefs;
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  private Map<String, Ref> doGetRemoteRefs(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull LsRemoteCommand command) throws Exception {
    return command.lsRemote(db, gitRoot, new FetchSettings(gitRoot.getAuthSettings(), createProgress()));
  }

  @NotNull
  private GitProgress createProgress() {
    try {
      return new GitVcsOperationProgress(myProgressProvider.getProgress());
    } catch (IllegalStateException e) {
      return GitProgress.NO_OP;
    }
  }

  public Collection<VcsClientMapping> getClientMapping(final @NotNull VcsRoot root, final @NotNull IncludeRule rule) throws VcsException {

    // Using more verbose code as constructor of GitVcsRoot in CPU-hungry, see TW-67881
    final URIishHelperImpl urIishHelper = new URIishHelperImpl();

    final AuthSettings auth = createAuthSettings(root, urIishHelper);
    final CommonURIish uri = urIishHelper.createAuthURI(auth, root.getProperty(Constants.FETCH_URL));

    return Collections.singletonList(new VcsClientMapping(String.format("|%s|%s", uri.toString(), rule.getFrom()), rule.getTo()));
  }

  private AuthSettings createAuthSettings(@NotNull VcsRoot root, URIishHelperImpl urIishHelper) {
    // Avoiding root.getProperties() call as it wraps properties to TreeMap with sorting.
    Map<String, String> authProps = new HashMap<>();
    authProps.put(Constants.AUTH_METHOD, root.getProperty(Constants.AUTH_METHOD));
    authProps.put(Constants.PASSPHRASE, root.getProperty(Constants.PASSPHRASE));
    authProps.put(Constants.USERNAME, root.getProperty(Constants.USERNAME));
    authProps.put(Constants.PASSWORD, root.getProperty(Constants.PASSWORD));
    authProps.put(Constants.PRIVATE_KEY_PATH, root.getProperty(Constants.PRIVATE_KEY_PATH));
    authProps.put(Constants.FETCH_URL, root.getProperty(Constants.FETCH_URL));
    return new AuthSettingsImpl(authProps, urIishHelper);
  }

  @Override
  public boolean isDAGBasedVcs() {
    return true;
  }

  @Override
  public ListFilesPolicy getListFilesPolicy() {
    return new ListFilesDispatcher(this, myCommitLoader, myConfig);
  }

  @NotNull
  @Override
  public Map<String, String> getCheckoutProperties(@NotNull VcsRoot root) {
    Map<String, String> defaults = getDefaultVcsProperties();
    Set<String> significantProps = setOf(Constants.FETCH_URL,
                                         Constants.SUBMODULES_CHECKOUT,
                                         Constants.AGENT_CLEAN_POLICY,
                                         Constants.AGENT_CLEAN_FILES_POLICY);
    Map<String, String> rootProperties = root.getProperties();
    Map<String, String> repositoryProperties = new HashMap<String, String>();
    for (String key : significantProps) {
      String defVal = defaults.get(key);
      String actualVal = rootProperties.get(key);
      repositoryProperties.put(key, actualVal == null ? defVal : actualVal);
    }

    //include autocrlf settings only for non-default value
    //in order to avoid clean checkout
    if ("true".equals(rootProperties.get(Constants.SERVER_SIDE_AUTO_CRLF)))
      repositoryProperties.put(Constants.SERVER_SIDE_AUTO_CRLF, rootProperties.get(Constants.SERVER_SIDE_AUTO_CRLF));

    return repositoryProperties;
  }

  @Override
  @Nullable
  protected <T extends VcsExtension> T getVcsCustomExtension(@NotNull final Class<T> extensionClass) {
    if (ChangesInfoBuilder.class.equals(extensionClass)) {
      return extensionClass.cast(getCollectChangesPolicy());
    }

    for (GitServerExtension e : myExtensions) {
      if (extensionClass.isInstance(e))
        return extensionClass.cast(e);
    }
    return super.getVcsCustomExtension(extensionClass);
  }

  @NotNull
  public CommitLoader getCommitLoader() {
    return myCommitLoader;
  }

  @NotNull
  public RepositoryManager getRepositoryManager() {
    return myRepositoryManager;
  }

  @NotNull
  @Override
  public String getVcsRootIdentity(@NotNull VcsRoot vcsRoot) throws VcsException {
    final OperationContext context = createContext(vcsRoot, "get vcs root identity");
    try {
      final GitVcsRoot gitRoot = context.getGitRoot();
      // we include "Use tags as branches" setting here, because the same repo with and without tags turns out to be different
      // entities from the point of view of TeamCity core (at least when working with current state)
      return gitRoot.getRepositoryDir().getName() + (gitRoot.isReportTags() ? ".inclTags" : "");
    } finally {
      context.close();
    }
  }

  @Nullable
  @Override
  public String getDefaultBranchName(@NotNull VcsRoot vcsRoot) {
    final String prop = vcsRoot.getProperty(Constants.BRANCH_NAME);
    return prop == null ? null : GitUtils.expandRef(prop);
  }

  @Nullable
  public ServerMetrics getServerMetrics() {
    return myServerMetrics;
  }

  public boolean isNativeGitOperationEnabled(@NotNull GitVcsRoot root) {
    return myGitRepoOperations.isNativeGitOperationsEnabled(root.getRepositoryFetchURL().toString());
  }

  @NotNull
  public GitRepoOperations getGitRepoOperations() {
    return myGitRepoOperations;
  }
}