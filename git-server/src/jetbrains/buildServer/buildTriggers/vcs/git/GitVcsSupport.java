/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchBuilder;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ConnectException;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.getRevision;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsPersonalSupport, BuildPatchByCheckoutRules,
             TestConnectionSupport, IncludeRuleBasedMappingProvider {

  private static final Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  private ExtensionHolder myExtensionHolder;
  private volatile String myDisplayName = null;
  private final ServerPluginConfig myConfig;
  private final TransportFactory myTransportFactory;
  private final RepositoryManager myRepositoryManager;
  private final GitMapFullPath myMapFullPath;
  private final CommitLoader myCommitLoader;
  private Collection<GitServerExtension> myExtensions = new ArrayList<GitServerExtension>();

  public GitVcsSupport(@NotNull ServerPluginConfig config,
                       @NotNull ResetCacheRegister resetCacheManager,
                       @NotNull TransportFactory transportFactory,
                       @NotNull RepositoryManager repositoryManager,
                       @NotNull GitMapFullPath mapFullPath,
                       @NotNull CommitLoader commitLoader) {
    myConfig = config;
    myTransportFactory = transportFactory;
    myRepositoryManager = repositoryManager;
    myMapFullPath = mapFullPath;
    myCommitLoader = commitLoader;
    setStreamFileThreshold();
    resetCacheManager.registerHandler(new GitResetCacheHandler(repositoryManager));
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
    WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setStreamFileThreshold(myConfig.getStreamFileThreshold() * WindowCacheConfig.MB);
    WindowCache.reconfigure(cfg);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot fromRoot,
                                               @NotNull String fromVersion,
                                               @NotNull VcsRoot toRoot,
                                               @Nullable String toVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    return getCollectChangesPolicy().collectChanges(fromRoot, fromVersion, toRoot, toVersion, checkoutRules);
  }

  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    return getCollectChangesPolicy().collectChanges(root, fromVersion, currentVersion, checkoutRules);
  }


  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot root) throws VcsException {
    GitVcsRoot gitRoot = new GitVcsRoot(myRepositoryManager, root);
    return getCurrentState(gitRoot);
  }

  @NotNull
  public RepositoryStateData getCurrentState(@NotNull GitVcsRoot gitRoot) throws VcsException {
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
    if (branchRevisions.get(fullRef) == null) {
      throw new VcsException("Cannot find revision of the default branch '" + refInRoot + "' of vcs root " + LogUtil.describe(gitRoot));
    }
    return RepositoryStateData.createVersionState(fullRef, branchRevisions);
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
    GitPatchBuilder gitPatchBuilder = new GitPatchBuilder(myConfig, context, builder, fromRevision, toRevision, checkoutRules);
    try {
      myCommitLoader.loadCommit(context, context.getGitRoot(), toRevision);
      gitPatchBuilder.buildPatch();
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
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
    TestConnectionCommand command = new TestConnectionCommand(this, myTransportFactory, myRepositoryManager);
    try {
      return command.testConnection(context);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @Override
  public TestConnectionSupport getTestConnectionSupport() {
    return this;
  }

  public OperationContext createContext(VcsRoot root, String operation) {
    return new OperationContext(this, myCommitLoader, myRepositoryManager, root, operation);
  }

  @NotNull
  public LabelingSupport getLabelingSupport() {
    return new GitLabelingSupport(this, myCommitLoader, myRepositoryManager, myTransportFactory);
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return new GitFileContentDispatcher(this, myCommitLoader, myConfig);
  }

  @NotNull
  public GitCollectChangesPolicy getCollectChangesPolicy() {
    return new GitCollectChangesPolicy(this, myCommitLoader, myConfig);
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
      return myMapFullPath.mapFullPath(context, rootEntry, fullPath);
    } catch (VcsException e) {
      LOG.error(e);
      return Collections.emptySet();
    } finally {
      context.close();
    }
  }

  @Override
  public boolean isAgentSideCheckoutAvailable() {
    return true;
  }


  @Override
  public UrlSupport getUrlSupport() {
    return new GitUrlSupport(this);
  }


  @NotNull
  public Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "list remote refs");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      Repository db = context.getRepository();
      return getRemoteRefs(db, gitRoot);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  private Map<String, Ref> getRemoteRefs(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws Exception {
    int attemptsLeft = myConfig.getConnectionRetryAttempts();
    while (true) {
      final long start = System.currentTimeMillis();
      Transport transport = null;
      FetchConnection connection = null;
      try {
        transport = myTransportFactory.createTransport(db, gitRoot.getRepositoryFetchURL(), gitRoot.getAuthSettings());
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
      Thread.sleep(myConfig.getConnectionRetryIntervalMillis());
    }
  }

  private boolean isRecoverable(@NotNull TransportException e) {
    String message = e.getMessage();
    if (message == null)
      return false;
    Throwable cause = e.getCause();
    if (cause instanceof JSchException) {
      return message.contains("Session.connect: java.net.SocketException: Connection reset") ||
             message.contains("connection is closed by foreign host") ||
             message.contains("java.net.UnknownHostException:") || //TW-31027
             message.contains("com.jcraft.jsch.JSchException: verify: false"); //TW-31175
    }
    if (cause instanceof ConnectException) {
      return message.contains("Connection timed out");
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
}
