/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchBuilder;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsPersonalSupport, BuildPatchByCheckoutRules,
             TestConnectionSupport, BranchSupport, IncludeRuleBasedMappingProvider {

  private static final Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  private final ExtensionHolder myExtensionHolder;
  private volatile String myDisplayName = null;
  private final ServerPluginConfig myConfig;
  private final TransportFactory myTransportFactory;
  private final FetchCommand myFetchCommand;
  private final RepositoryManager myRepositoryManager;
  private final GitMapFullPath myMapFullPath;


  public GitVcsSupport(@NotNull ServerPluginConfig config,
                       @NotNull ResetCacheRegister resetCacheManager,
                       @NotNull TransportFactory transportFactory,
                       @NotNull FetchCommand fetchCommand,
                       @NotNull RepositoryManager repositoryManager,
                       @NotNull GitMapFullPath mapFullPath,
                       @Nullable ExtensionHolder extensionHolder) {
    myConfig = config;
    myExtensionHolder = extensionHolder;
    myTransportFactory = transportFactory;
    myFetchCommand = fetchCommand;
    myRepositoryManager = repositoryManager;
    myMapFullPath = mapFullPath;
    setStreamFileThreshold();
    resetCacheManager.registerHandler(new GitResetCacheHandler(repositoryManager));
    myMapFullPath.setGitVcs(this);
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
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }

  @NotNull
  public RepositoryState getCurrentState(@NotNull VcsRoot root) throws VcsException {
    GitVcsRoot gitRoot = new GitVcsRoot(myRepositoryManager, root);
    String refInRoot = gitRoot.getRef();
    String fullRef = GitUtils.expandRef(refInRoot);
    Map<String, String> branchRevisions = new HashMap<String, String>();
    for (Ref ref : getRemoteRefs(root).values()) {
      if (!ref.getName().startsWith("ref"))
        continue;
      if (isTag(ref) && !fullRef.equals(ref.getName()))
        continue;
      branchRevisions.put(ref.getName(), ref.getObjectId().name());
    }
    if (branchRevisions.get(fullRef) == null) {
      VcsException e = new VcsException("Cannot find revision of the default branch '" +
              refInRoot + "' of vcs root " + LogUtil.describe(root));
      e.setRoot(root);
      throw e;
    }
    return RepositoryStateFactory.createRepositoryState(branchRevisions, fullRef);
  }

  @NotNull
  public Map<String, String> getBranchRootOptions(@NotNull VcsRoot root, @NotNull String branchName) {
    final Map<String, String> result = new HashMap<String, String>(root.getProperties());
    result.put(Constants.BRANCH_NAME, branchName);
    return result;
  }


  @Nullable
  public PersonalBranchDescription getPersonalBranchDescription(@NotNull VcsRoot original, @NotNull String branchName) throws VcsException {
    VcsRoot branchRoot = createBranchRoot(original, branchName);
    OperationContext context = createContext(branchRoot, "find fork version");
    PersonalBranchDescription result = null;
    RevWalk walk = null;
    try {
      String originalCommit = getCurrentVersion(original);
      String branchCommit   = getCurrentVersion(branchRoot);
      ensureCommitLoaded(context, context.getGitRoot(original), originalCommit);
      ensureCommitLoaded(context, context.getGitRoot(branchRoot), branchCommit);

      Repository db = context.getRepository();
      walk = new RevWalk(db);
      walk.markStart(walk.parseCommit(ObjectId.fromString(branchCommit)));
      walk.markUninteresting(walk.parseCommit(ObjectId.fromString(originalCommit)));
      walk.sort(RevSort.TOPO);
      boolean lastCommit = true;
      String firstCommitInBranch = null;
      String lastCommitUser = null;
      RevCommit c;
      while ((c = walk.next()) != null) {
        if (lastCommit) {
          lastCommitUser = GitServerUtil.getUser(context.getGitRoot(), c);
          lastCommit = false;
        }
        firstCommitInBranch = c.name();
      }
      if (firstCommitInBranch != null && lastCommitUser != null)
        result = new PersonalBranchDescription(firstCommitInBranch, lastCommitUser);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      try {
        if (walk != null)
          walk.release();
      } finally {
        context.close();
      }
    }
    return result;
  }

  private VcsRoot createBranchRoot(VcsRoot original, String branchName) {
    VcsRootImpl result = new VcsRootImpl(original.getId(), original.getVcsName());
    result.addAllProperties(original.getProperties());
    result.addProperty(Constants.BRANCH_NAME, branchName);
    return result;
  }

  public void buildPatch(@NotNull VcsRoot root,
                         @Nullable String fromVersion,
                         @NotNull String toVersion,
                         @NotNull PatchBuilder builder,
                         @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
    OperationContext context = createContext(root, "patch building");
    String fromRevision = fromVersion != null ? GitUtils.versionRevision(fromVersion) : null;
    String toRevision = GitUtils.versionRevision(toVersion);
    GitPatchBuilder gitPatchBuilder = new GitPatchBuilder(myConfig, context, builder, fromRevision, toRevision, checkoutRules);
    try {
      ensureRevCommitLoaded(context, context.getGitRoot(), toRevision);
      gitPatchBuilder.buildPatch();
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  RevCommit ensureCommitLoaded(@NotNull OperationContext context,
                               @NotNull GitVcsRoot root,
                               @NotNull String revision) throws Exception {
    final String commit = GitUtils.versionRevision(revision);
    return ensureRevCommitLoaded(context, root, commit);
  }

  @NotNull
  private RevCommit ensureRevCommitLoaded(@NotNull OperationContext context,
                                          @NotNull GitVcsRoot root,
                                          @NotNull String commitSHA) throws Exception {
    Repository db = context.getRepository(root);
    try {
      return getCommit(db, commitSHA);
    } catch (IOException ex) {
      //ignore error, will try to fetch
    }

    LOG.debug("Cannot find commit " + commitSHA + " in repository " + root.debugInfo() + ", fetch branch " + root.getRef());
    fetchBranchData(root, db);

    try {
      return getCommit(db, commitSHA);
    } catch (IOException e) {
      LOG.debug("Cannot find commit " + commitSHA + " in the branch " + root.getRef() +
                " of repository " + root.debugInfo() + ", fetch all branches");
      RefSpec spec = new RefSpec().setSourceDestination("refs/heads/*", "refs/heads/*").setForceUpdate(true);
      fetch(db, root.getRepositoryFetchURL(), spec, root.getAuthSettings());
      try {
        return getCommit(db, commitSHA);
      } catch (IOException e1) {
        throw new VcsException("Cannot find commit " + commitSHA + " in repository " + root.debugInfo());
      }
    }
  }

  public RevCommit getCommit(Repository repository, String commitSHA) throws IOException {
    return getCommit(repository, ObjectId.fromString(commitSHA));
  }

  public RevCommit getCommit(Repository repository, ObjectId commitId) throws IOException {
    final long start = System.currentTimeMillis();
    RevWalk walk = new RevWalk(repository);
    try {
      return walk.parseCommit(commitId);
    } finally {
      walk.release();
      final long finish = System.currentTimeMillis();
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug("[RevWalk.parseCommit] repository=" + repository.getDirectory().getAbsolutePath() + ", commit=" + commitId.name() + ", took: " + (finish - start) + "ms");
      }
    }
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
          myDisplayName = "Git (Jetbrains plugin)";
        } else {
          myDisplayName = "Git";
        }
      } else {
        myDisplayName = "Git (Jetbrains plugin)";
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
  public String describeVcsRoot(VcsRoot root) {
    final String branch = root.getProperty(Constants.BRANCH_NAME);
    return root.getProperty(Constants.FETCH_URL) + "#" + (branch == null ? "master" : branch);
  }

  public Map<String, String> getDefaultVcsProperties() {
    final HashMap<String, String> map = new HashMap<String, String>();
    map.put(Constants.BRANCH_NAME, "master");
    map.put(Constants.IGNORE_KNOWN_HOSTS, "true");
    return map;
  }

  public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) throws VcsException {
    return GitServerUtil.displayVersion(version);
  }

  @NotNull
  public Comparator<String> getVersionComparator() {
    return GitUtils.VERSION_COMPARATOR;
  }

  @NotNull
  public String getCurrentVersion(@NotNull VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "retrieving current version");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      Repository db = context.getRepository();
      String refName = GitUtils.expandRef(gitRoot.getRef());
      Ref remoteRef = getRemoteRef(gitRoot, db, refName);
      if (remoteRef == null)
        throw new VcsException("The ref '" + refName + "' could not be resolved");
      return remoteRef.getObjectId().name();
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  private boolean isRemoteRefUpdated(@NotNull Repository db, @NotNull Ref remoteRef) throws IOException {
    Ref localRef = db.getRef(remoteRef.getName());
    return localRef == null || !remoteRef.getObjectId().name().equals(localRef.getObjectId().name());
  }


  public boolean sourcesUpdatePossibleIfChangesNotFound(@NotNull VcsRoot root) {
    return false;
  }

  /**
   * Fetch data for the branch
   *
   * @param root git root
   * @param repository the repository
   * @throws Exception if there is a problem with fetching data
   */
  private void fetchBranchData(@NotNull GitVcsRoot root, @NotNull Repository repository) throws Exception {
    final String refName = GitUtils.expandRef(root.getRef());
    RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
    fetch(repository, root.getRepositoryFetchURL(), spec, root.getAuthSettings());
  }


  public void fetch(Repository db, URIish fetchURI, Collection<RefSpec> refspecs, GitVcsRoot.AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
    File repositoryDir = db.getDirectory();
    assert repositoryDir != null : "Non-local repository";
    Lock rmLock = myRepositoryManager.getRmLock(repositoryDir).readLock();
    rmLock.lock();
    try {
      final long start = System.currentTimeMillis();
      synchronized (myRepositoryManager.getWriteLock(repositoryDir)) {
        final long finish = System.currentTimeMillis();
        PERFORMANCE_LOG.debug("[waitForWriteLock] repository: " + repositoryDir.getAbsolutePath() + ", took " + (finish - start) + "ms");
        myFetchCommand.fetch(db, fetchURI, refspecs, auth);
        myMapFullPath.invalidateRevisionsCache(db);
      }
    } finally {
      rmLock.unlock();
    }
  }
  /**
   * Make fetch into local repository (it.s getDirectory() should be != null)
   *
   * @param db repository
   * @param fetchURI uri to fetch
   * @param refspec refspec
   * @param auth auth settings
   */
  public void fetch(Repository db, URIish fetchURI, RefSpec refspec, GitVcsRoot.AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
    fetch(db, fetchURI, Collections.singletonList(refspec), auth);
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
    return new OperationContext(this, myRepositoryManager, root, operation);
  }

  @NotNull
  public LabelingSupport getLabelingSupport() {
    return new GitLabelingSupport(this, myRepositoryManager, myTransportFactory);
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return new GitVcsFileContentProvider(this, myConfig);
  }

  @NotNull
  public GitCollectChangesPolicy getCollectChangesPolicy() {
    return new GitCollectChangesPolicy(this, myConfig);
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
    return new GitUrlSupport();
  }


  @NotNull
  private Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "list remote refs");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      Repository db = context.getRepository();
      return getRemoteRefs(root, db, gitRoot);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  private Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root, @NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws Exception {
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
      throw friendlyTransportException(te);
    } finally {
      if (connection != null)
        connection.close();
      if (transport != null)
        transport.close();
      final long finish = System.currentTimeMillis();
      PERFORMANCE_LOG.debug("[getRemoteRefs] repository: " + LogUtil.describe(root) + ", took " + (finish - start) + "ms");
    }
  }

  @Nullable
  private Ref getRemoteRef(@NotNull GitVcsRoot root, @NotNull Repository db, @NotNull String refName) throws Exception {
    final long start = System.currentTimeMillis();
    Transport transport = null;
    FetchConnection connection = null;
    try {
      transport = myTransportFactory.createTransport(db, root.getRepositoryFetchURL(), root.getAuthSettings());
      connection = transport.openFetch();
      return connection.getRef(refName);
    } catch (NotSupportedException nse) {
      throw friendlyNotSupportedException(root, nse);
    } catch (TransportException te) {
      throw friendlyTransportException(te);
    } finally {
      if (connection != null)
        connection.close();
      if (transport != null)
        transport.close();
      final long finish = System.currentTimeMillis();
      PERFORMANCE_LOG.debug("[getRemoteRef] repository: " + LogUtil.describe(root) + ", ref: '" + refName + "', took " + (finish - start) + "ms");
    }
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
    return new GitListFilesSupport(this);
  }

  @NotNull
  @Override
  public Map<String, String> getCheckoutProperties(@NotNull VcsRoot root) throws VcsException {
    Set<String> repositoryPropertyKeys = setOf(Constants.FETCH_URL,
                                               Constants.SUBMODULES_CHECKOUT,
                                               Constants.AGENT_CLEAN_POLICY,
                                               Constants.AGENT_CLEAN_FILES_POLICY);
    Map<String, String> rootProperties = root.getProperties();
    Map<String, String> repositoryProperties = new HashMap<String, String>();
    for (String key : repositoryPropertyKeys)
      repositoryProperties.put(key, rootProperties.get(key));
    return repositoryProperties;
  }
}
