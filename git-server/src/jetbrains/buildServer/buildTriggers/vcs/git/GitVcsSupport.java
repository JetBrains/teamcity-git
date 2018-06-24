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

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.JSchException;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchBuilderDispatcher;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.getRevision;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsBulkSuitabilityChecker, BuildPatchByCheckoutRules,
             TestConnectionSupport, IncludeRuleBasedMappingProvider {

  private static final Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  static final String GIT_REPOSITORY_HAS_NO_BRANCHES = "Git repository has no branches";

  private ExtensionHolder myExtensionHolder;
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
  private Collection<GitServerExtension> myExtensions = new ArrayList<GitServerExtension>();

  public GitVcsSupport(@NotNull ServerPluginConfig config,
                       @NotNull ResetCacheRegister resetCacheManager,
                       @NotNull TransportFactory transportFactory,
                       @NotNull RepositoryManager repositoryManager,
                       @NotNull GitMapFullPath mapFullPath,
                       @NotNull CommitLoader commitLoader,
                       @NotNull VcsRootSshKeyManager sshKeyManager,
                       @NotNull VcsOperationProgressProvider progressProvider,
                       @NotNull GitResetCacheHandler resetCacheHandler,
                       @NotNull ResetRevisionsCacheHandler resetRevisionsCacheHandler,
                       @Nullable TestConnectionSupport customTestConnection) {
    this(config, resetCacheManager, transportFactory, repositoryManager, mapFullPath, commitLoader, sshKeyManager, progressProvider,
         resetCacheHandler, resetRevisionsCacheHandler, new GitTrustStoreProviderStatic(null), customTestConnection);
  }

  public GitVcsSupport(@NotNull ServerPluginConfig config,
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
                       @Nullable TestConnectionSupport customTestConnection) {
    myConfig = config;
    myTransportFactory = transportFactory;
    myRepositoryManager = repositoryManager;
    myMapFullPath = mapFullPath;
    myCommitLoader = commitLoader;
    mySshKeyManager = sshKeyManager;
    myProgressProvider = progressProvider;
    setStreamFileThreshold();
    resetCacheManager.registerHandler(resetCacheHandler);
    resetCacheManager.registerHandler(resetRevisionsCacheHandler);
    myGitTrustStoreProvider = gitTrustStoreProvider;
    myTestConnection = customTestConnection == null ? this : customTestConnection;
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
    GitVcsRoot fromGitRoot = new GitVcsRoot(myRepositoryManager, fromRoot);
    GitVcsRoot toGitRoot = new GitVcsRoot(myRepositoryManager, toRoot);
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
    GitVcsRoot gitRoot = new GitVcsRoot(myRepositoryManager, root);
    RepositoryStateData fromState = RepositoryStateData.createVersionState(gitRoot.getRef(), fromVersion);
    RepositoryStateData toState = RepositoryStateData.createVersionState(gitRoot.getRef(), currentVersion);
    return getCollectChangesPolicy().collectChanges(root, fromState, toState, checkoutRules);
  }


  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot root) throws VcsException {
    GitVcsRoot gitRoot = new GitVcsRoot(myRepositoryManager, root);
    return getCurrentState(gitRoot);
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
        if (!gitRoot.isReportTags() && isTag(ref) && !fullRef.equals(ref.getName()))
          continue;
        branchRevisions.put(ref.getName(), getRevision(ref));
      }
      if (branchRevisions.get(fullRef) == null && !gitRoot.isIgnoreMissingDefaultBranch()) {
        if (branchRevisions.isEmpty()) {
          throw new VcsException(GIT_REPOSITORY_HAS_NO_BRANCHES);
        } else {
          throw new VcsException("Cannot find revision of the default branch '" + refInRoot + "' of vcs root '" + gitRoot.getName() + "'");
        }
      }
      return RepositoryStateData.createVersionState(fullRef, branchRevisions);
    });
  }

  public void buildPatch(@NotNull VcsRoot root,
                         @Nullable String fromVersion,
                         @NotNull String toVersion,
                         @NotNull PatchBuilder builder,
                         @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
    OperationContext context = createContext(root, "patch building");
    String fromRevision = fromVersion != null ? GitUtils.versionRevision(fromVersion) : null;
    String toRevision = GitUtils.versionRevision(toVersion);
    logBuildPatch(root, fromRevision, toRevision);
    GitVcsRoot gitRoot = context.getGitRoot();
    myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      GitPatchBuilderDispatcher gitPatchBuilder = new GitPatchBuilderDispatcher(myConfig, mySshKeyManager, context, builder, fromRevision,
                                                                                toRevision, checkoutRules,
                                                                                myGitTrustStoreProvider.serialize());
      try {
        myCommitLoader.loadCommit(context, gitRoot, toRevision);
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
    final HashMap<String, String> map = new HashMap<String, String>();
    map.put(Constants.BRANCH_NAME, "refs/heads/master");
    map.put(Constants.IGNORE_KNOWN_HOSTS, "true");
    map.put(Constants.AUTH_METHOD, AuthenticationMethod.ANONYMOUS.name());
    map.put(Constants.USERNAME_STYLE, GitVcsRoot.UserNameStyle.USERID.name());
    map.put(Constants.AGENT_CLEAN_POLICY, AgentCleanPolicy.ON_BRANCH_CHANGE.name());
    map.put(Constants.AGENT_CLEAN_FILES_POLICY, AgentCleanFilesPolicy.ALL_UNTRACKED.name());
    map.put(Constants.SUBMODULES_CHECKOUT, SubmodulesCheckoutPolicy.CHECKOUT.name());
    map.put(Constants.USE_AGENT_MIRRORS, "true");
    return map;
  }

  @NotNull
  public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) throws VcsException {
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

  public OperationContext createContext(@NotNull String operation) {
    return createContext(null, operation);
  }

  public OperationContext createContext(VcsRoot root, String operation) {
    return createContext(root, operation, GitProgress.NO_OP);
  }

  public OperationContext createContext(@Nullable VcsRoot root, @NotNull String operation, @NotNull GitProgress progress) {
    return new OperationContext(myCommitLoader, myRepositoryManager, root, operation, progress, myConfig);
  }

  @NotNull
  public LabelingSupport getLabelingSupport() {
    return new GitLabelingSupport(this, myCommitLoader, myRepositoryManager, myTransportFactory, myConfig);
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return new GitFileContentDispatcher(this, myCommitLoader, myConfig);
  }

  @NotNull
  public GitCollectChangesPolicy getCollectChangesPolicy() {
    return new GitCollectChangesPolicy(this, myProgressProvider, myCommitLoader, myConfig, myRepositoryManager);
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
  public List<Boolean> checkSuitable(@NotNull List<VcsRootEntry> entries, @NotNull Collection<String> paths) throws VcsException {
    Set<GitMapFullPath.FullPath> fullPaths = paths.stream().map(GitMapFullPath.FullPath::new).collect(Collectors.toSet());

    //checkout rules do not affect suitability, we can check it for unique root only ignoring different checkout rules
    Set<VcsRoot> uniqueRoots = entries.stream().map(VcsRootEntry::getVcsRoot).collect(Collectors.toSet());

    //several roots with different settings can be cloned into the same dir,
    //do not compute suitability for given clone dir more than once
    Map<File, Boolean> cloneDirResults = new HashMap<>();//clone dir -> result for this dir
    Map<VcsRoot, Boolean> rootResult = new HashMap<>();
    for (VcsRoot root : uniqueRoots) {
      OperationContext context = createContext(root, "checkSuitable");
      try {
        GitVcsRoot gitRoot = context.getGitRoot();
        File cloneDir = gitRoot.getRepositoryDir();
        Boolean cloneDirResult = cloneDirResults.get(cloneDir);
        if (cloneDirResult != null) {
          rootResult.put(root, cloneDirResult);
          continue;
        }

        boolean suitable = myRepositoryManager.runWithDisabledRemove(cloneDir, () -> {
          for (GitMapFullPath.FullPath path : fullPaths) {
            if (myMapFullPath.repositoryContainsPath(context, gitRoot, path))
              return true;
          }
          return false;
        });

        rootResult.put(root, suitable);
        cloneDirResults.put(gitRoot.getRepositoryDir(), suitable);
      } catch (VcsException e) {
        //will return false for broken VCS root
        LOG.warnAndDebugDetails("Error while checking suitability for root " + LogUtil.describe(root) + ", assume root is not suitable", e);
      } finally {
        context.close();
      }
    }

    List<Boolean> result = new ArrayList<>();
    for (VcsRootEntry entry : entries) {
      Boolean suitable = rootResult.get(entry.getVcsRoot());
      if (suitable != null) {
        result.add(suitable);
      } else {
        //can be null if the root was broken
        result.add(false);
      }
    }
    return result;
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
  public Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "list remote refs");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      Repository db = context.getRepository();
      Map<String, Ref> remoteRefs = getRemoteRefs(db, gitRoot);
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
  private Map<String, Ref> getRemoteRefs(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws Exception {
    long retryInterval = myConfig.getConnectionRetryIntervalMillis();
    int attemptsLeft = myConfig.getConnectionRetryAttempts();
    int timeout = myConfig.getRepositoryStateTimeoutSeconds();
    while (true) {
      final long start = System.currentTimeMillis();
      Transport transport = null;
      FetchConnection connection = null;
      try {
        transport = myTransportFactory.createTransport(db, gitRoot.getRepositoryFetchURL(), gitRoot.getAuthSettings(), timeout);
        connection = transport.openFetch();
        return connection.getRefsMap();
      } catch (NotSupportedException nse) {
        throw friendlyNotSupportedException(gitRoot, nse);
      } catch (TransportException te) {
        attemptsLeft--;
        if (isRecoverable(te) && attemptsLeft > 0) {
          LOG.warn("List remote refs failed: " + te.getMessage() + ", " + attemptsLeft + " attempt(s) left");
        } else {
          throw friendlyTransportException(te, gitRoot);
        }
      } catch (WrongPassphraseException e) {
        throw new VcsException(e.getMessage(), e);
      } finally {
        if (connection != null)
          connection.close();
        if (transport != null)
          transport.close();
        final long finish = System.currentTimeMillis();
        PERFORMANCE_LOG.debug("[getRemoteRefs] repository: " + LogUtil.describe(gitRoot) + ", took " + (finish - start) + "ms");
      }
      Thread.sleep(retryInterval);
      retryInterval *= 2;
    }
  }

  private boolean isRecoverable(@NotNull TransportException e) {
    String message = e.getMessage();
    if (message == null)
      return false;
    if (message.contains("Connection timed out") ||
        message.contains("Connection time out")) {
      return true;
    }
    Throwable cause = e.getCause();
    if (cause instanceof JSchException) {
      return message.contains("Session.connect: java.net.SocketException: Connection reset") ||
             message.contains("Session.connect: java.net.SocketException: Software caused connection abort") ||
             message.contains("Session.connect: java.net.SocketTimeoutException: Read timed out") ||
             message.contains("connection is closed by foreign host") ||
             message.contains("timeout: socket is not established") ||
             message.contains("java.net.UnknownHostException:") || //TW-31027
             message.contains("com.jcraft.jsch.JSchException: verify: false"); //TW-31175
    }
    return false;
  }


  public Collection<VcsClientMapping> getClientMapping(final @NotNull VcsRoot root, final @NotNull IncludeRule rule) throws VcsException {
    final OperationContext context = createContext(root, "client-mapping");
    try {
      GitVcsRoot gitRoot = context.getGitRoot();
      URIish uri = gitRoot.getRepositoryFetchURL();
      return Collections.singletonList(new VcsClientMapping(String.format("|%s|%s", uri.toString(), rule.getFrom()), rule.getTo()));
    } finally {
      context.close();
    }
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
  public Map<String, String> getCheckoutProperties(@NotNull VcsRoot root) throws VcsException {
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

    if (myExtensions != null) {
      for (GitServerExtension e : myExtensions) {
        if (extensionClass.isInstance(e))
          return extensionClass.cast(e);
      }
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
}
