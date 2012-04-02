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
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.browse.EmptyBrowser;
import jetbrains.buildServer.buildTriggers.vcs.git.browse.GitBrowser;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.RepositoryState;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsPersonalSupport, LabelingSupport, VcsFileContentProvider, CollectChangesBetweenRoots, BuildPatchByCheckoutRules,
             TestConnectionSupport, BranchSupport, IncludeRuleBasedMappingProvider, BrowseSupport {

  private static Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  /**
   * Current version cache (Pair<bare repository dir, branch name> -> current version).
   */
  private final RecentEntriesCache<Pair<File, String>, String> myCurrentVersionCache;

  private final ExtensionHolder myExtensionHolder;
  private volatile String myDisplayName = null;
  private final ServerPluginConfig myConfig;
  private final TransportFactory myTransportFactory;
  private final FetchCommand myFetchCommand;
  private final RepositoryManager myRepositoryManager;


  public GitVcsSupport(@NotNull final ServerPluginConfig config,
                       @NotNull final ResetCacheRegister resetCacheManager,
                       @NotNull final TransportFactory transportFactory,
                       @NotNull final FetchCommand fetchCommand,
                       @NotNull final RepositoryManager repositoryManager,
                       @Nullable final ExtensionHolder extensionHolder) {
    myConfig = config;
    myExtensionHolder = extensionHolder;
    myTransportFactory = transportFactory;
    myFetchCommand = fetchCommand;
    myRepositoryManager = repositoryManager;
    myCurrentVersionCache = new RecentEntriesCache<Pair<File, String>, String>(myConfig.getCurrentVersionCacheSize());
    setStreamFileThreshold();
    resetCacheManager.registerHandler(new GitResetCacheHandler(this, repositoryManager));
  }


  private void setStreamFileThreshold() {
    WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setStreamFileThreshold(myConfig.getStreamFileThreshold() * WindowCacheConfig.MB);
    WindowCache.reconfigure(cfg);
  }


  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot originalRoot,
                                               @NotNull String  originalRootVersion,
                                               @NotNull VcsRoot branchRoot,
                                               @Nullable String  branchRootVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    LOG.debug("Collecting changes [" +LogUtil.describe(originalRoot) + "-" + originalRootVersion + "].." +
              "[" + LogUtil.describe(branchRoot) + "-" + branchRootVersion + "]");
    if (branchRootVersion == null) {
      LOG.warn("Branch root version is null for " + LogUtil.describe(branchRoot) + ", return empty list of changes");
      return Collections.emptyList();
    }
    String forkPoint = getLastCommonVersion(originalRoot, originalRootVersion, branchRoot, branchRootVersion);
    return collectChanges(branchRoot, forkPoint, branchRootVersion, checkoutRules);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    List<ModificationData> result = new ArrayList<ModificationData>();
    OperationContext context = createContext(root, "collecting changes");
    try {
      LOG.debug("Collecting changes " + fromVersion + ".." + currentVersion + " for " + context.getSettings().debugInfo());
      if (currentVersion == null) {
        LOG.warn("Current version is null for " + context.getSettings().debugInfo() + ", return empty list of changes");
        return result;
      }
      String upperBoundSHA = GitUtils.versionRevision(currentVersion);
      ensureRevCommitLoaded(context, context.getSettings(), upperBoundSHA);
      String lowerBoundSHA = GitUtils.versionRevision(fromVersion);
      Repository r = context.getRepository();
      result.addAll(getModifications(context, r, upperBoundSHA, lowerBoundSHA));
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
    return result;
  }


  private List<ModificationData> getModifications(@NotNull final OperationContext context, @NotNull final Repository r, @NotNull final String upperBoundSHA, @NotNull final String lowerBoundSHA) throws VcsException, IOException {
    List<ModificationData> modifications = new ArrayList<ModificationData>();
    ModificationDataRevWalk revWalk = new ModificationDataRevWalk(context, myConfig.getFixedSubmoduleCommitSearchDepth());
    revWalk.sort(RevSort.TOPO);
    try {
      revWalk.markStart(revWalk.parseCommit(ObjectId.fromString(upperBoundSHA)));
      ObjectId lowerBoundId = ObjectId.fromString(lowerBoundSHA);
      if (r.hasObject(lowerBoundId)) {
        revWalk.markUninteresting(revWalk.parseCommit(lowerBoundId));
      } else {
        LOG.warn("From version " + lowerBoundSHA + " is not found, collect last " + myConfig.getNumberOfCommitsWhenFromVersionNotFound() + " commits");
        revWalk.limitByNumberOfCommits(myConfig.getNumberOfCommitsWhenFromVersionNotFound());
      }
      while (revWalk.next() != null) {
        modifications.add(revWalk.createModificationData());
      }
      return modifications;
    } finally {
      revWalk.release();
    }
  }


  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }

  @NotNull
  public RepositoryState getCurrentState(@NotNull VcsRoot root) throws VcsException {
    Map<String, String> branchRevisions = new HashMap<String, String>();
    for (Ref ref : getRemoteRefs(root).values()) {
      branchRevisions.put(ref.getName(), ref.getObjectId().name());
    }
    return RepositoryStateFactory.createRepositoryState(branchRevisions);
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
      String originalCommit = GitUtils.versionRevision(getCurrentVersion(original));
      String branchCommit   = GitUtils.versionRevision(getCurrentVersion(branchRoot));
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
          lastCommitUser = GitServerUtil.getUser(context.getSettings(), c);
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

  private String getLastCommonVersion(VcsRoot baseRoot, String baseVersion, VcsRoot tipRoot, String tipVersion) throws VcsException {
    OperationContext context = createContext(tipRoot, "find fork version");
    Settings baseSettings = context.getSettings(baseRoot);
    Settings tipSettings = context.getSettings();
    LOG.debug("Find last common version between [" + baseSettings.debugInfo() + "-" + baseVersion + "].." +
              "[" + tipSettings.debugInfo() + "-" + tipVersion + "]");
    RevWalk walk = null;
    try {
      RevCommit baseCommit = ensureCommitLoaded(context, baseSettings, baseVersion);
      RevCommit tipCommit = ensureCommitLoaded(context, tipSettings, tipVersion);
      Repository tipRepository = context.getRepository(tipSettings);
      walk = new RevWalk(tipRepository);
      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(walk.parseCommit(baseCommit.getId()));
      walk.markStart(walk.parseCommit(tipCommit.getId()));
      final RevCommit base = walk.next();
      String result = GitServerUtil.makeVersion(base);
      LOG.debug("Last common revision between " + baseSettings.debugInfo() + " and " + tipSettings.debugInfo() + " is " + result);
      return result;
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
      ensureRevCommitLoaded(context, context.getSettings(), toRevision);
      gitPatchBuilder.buildPatch();
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  public byte[] getContent(@NotNull VcsModification vcsModification,
                           @NotNull VcsChangeInfo change,
                           @NotNull VcsChangeInfo.ContentType contentType,
                           @NotNull VcsRoot vcsRoot)
    throws VcsException {
    String version = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE
                     ? change.getBeforeChangeRevisionNumber()
                     : change.getAfterChangeRevisionNumber();
    String file = change.getRelativeFileName();
    return getContent(file, vcsRoot, version);
  }

  @NotNull
  public byte[] getContent(@NotNull String filePath, @NotNull VcsRoot root, @NotNull String version) throws VcsException {
    OperationContext context = createContext(root, "retrieving content");
    try {
      final long start = System.currentTimeMillis();
      Repository r = context.getRepository();
      final TreeWalk tw = new TreeWalk(r);
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Getting data from " + version + ":" + filePath + " for " + context.getSettings().debugInfo());
        }
        final String rev = GitUtils.versionRevision(version);
        RevCommit c = ensureRevCommitLoaded(context, context.getSettings(), rev);
        tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(filePath)));
        tw.setRecursive(tw.getFilter().shouldBeRecursive());
        context.addTree(tw, r, c, true);
        if (!tw.next()) {
          throw new VcsFileNotFoundException("The file " + filePath + " could not be found in " + rev + context.getSettings().debugInfo());
        }
        final byte[] data = loadObject(r, tw, 0);
        if (LOG.isDebugEnabled()) {
          LOG.debug(
            "File retrieved " + version + ":" + filePath + " (hash = " + tw.getObjectId(0) + ", length = " + data.length + ") for " +
            context.getSettings().debugInfo());
        }
        return data;
      } finally {
        final long finish = System.currentTimeMillis();
        if (PERFORMANCE_LOG.isDebugEnabled()) {
          PERFORMANCE_LOG.debug("[getContent] root=" + context.getSettings().debugInfo() + ", file=" + filePath + ", get object content: " + (finish - start) + "ms");
        }
        tw.release();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }

  /**
   * Load bytes that correspond to the position in the tree walker
   *
   * @param r   the initial repository
   * @param tw  the tree walker
   * @param nth the tree in the tree wailer
   * @return loaded bytes
   * @throws IOException if there is an IO error
   */
  private byte[] loadObject(Repository r, TreeWalk tw, final int nth) throws IOException {
    ObjectId id = tw.getObjectId(nth);
    Repository objRep = getRepository(r, tw, nth);
    final String path = tw.getPathString();
    return loadObject(objRep, path, id);
  }

  /**
   * Load object by blob ID
   *
   * @param r    the repository
   * @param path the path (might be null)
   * @param id   the object id
   * @return the object's bytes
   * @throws IOException in case of IO problem
   */
  private byte[] loadObject(Repository r, String path, ObjectId id) throws IOException {
    final ObjectLoader loader = r.open(id);
    if (loader == null) {
      throw new IOException("Unable to find blob " + id + (path == null ? "" : "(" + path + ")") + " in repository " + r);
    }
    if (loader.isLarge()) {
      assert loader.getSize() < Integer.MAX_VALUE;
      ByteArrayOutputStream output = new ByteArrayOutputStream((int) loader.getSize());
      loader.copyTo(output);
      return output.toByteArray();
    } else {
      return loader.getCachedBytes();
    }
  }

  @NotNull
  private RevCommit ensureCommitLoaded(OperationContext context, Settings rootSettings, String commitWithDate) throws Exception {
    final String commit = GitUtils.versionRevision(commitWithDate);
    return ensureRevCommitLoaded(context, rootSettings, commit);
  }

  @NotNull
  private RevCommit ensureRevCommitLoaded(OperationContext context, Settings settings, String commitSHA) throws Exception {
    Repository db = context.getRepository(settings);
    RevCommit result = null;
    try {
      final long start = System.currentTimeMillis();
      result = getCommit(db, commitSHA);
      final long finish = System.currentTimeMillis();
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug("[ensureCommitLoaded] root=" + settings.debugInfo() + ", commit=" + commitSHA + ", local commit lookup: " + (finish - start) + "ms");
      }
    } catch (IOException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("IO problem for commit " + commitSHA + " in " + settings.debugInfo(), ex);
      }
    }
    if (result == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Commit " + commitSHA + " is not in the repository for " + settings.debugInfo() + ", fetching data... ");
      }
      fetchBranchData(settings, db);
      result = getCommit(db, commitSHA);
      if (result == null) {
        throw new VcsException("The version name could not be resolved " + commitSHA + "(" + settings.getRepositoryFetchURL().toString() + "#" + settings.getRef() + ")");
      }
    }
    return result;
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
    map.put(Constants.AGENT_GIT_PATH, "%" + Constants.TEAMCITY_AGENT_GIT_PATH_FULL_NAME + "%");
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
    Settings s = context.getSettings();
    try {
      Repository r = context.getRepository();
      String refName = GitUtils.expandRef(s.getRef());

      if (!myConfig.isSeparateProcessForFetch() || isRemoteRefUpdated(root, r, s, refName))
        fetchBranchData(s, r);

      Ref branchRef = r.getRef(refName);
      if (branchRef == null) {
        throw new VcsException("The ref '" + refName + "' could not be resolved");
      }
      String cachedCurrentVersion = getCachedCurrentVersion(s.getRepositoryDir(), s.getRef());
      if (cachedCurrentVersion != null && GitUtils.versionRevision(cachedCurrentVersion).equals(branchRef.getObjectId().name())) {
        return cachedCurrentVersion;
      } else {
        RevCommit c = getCommit(r, branchRef.getObjectId());
        if (c == null) {
          throw new VcsException("The ref '" + refName + "' could not be resolved");
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Current version: " + c.getId().name() + " " + s.debugInfo());
        }
        final String currentVersion = GitServerUtil.makeVersion(c);
        setCachedCurrentVersion(s.getRepositoryDir(), s.getRef(), currentVersion);
        GitMapFullPath.invalidateRevisionsCache(root);
        return currentVersion;
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  private boolean isRemoteRefUpdated(@NotNull VcsRoot root, @NotNull Repository db, @NotNull Settings s, @NotNull String refName) throws Exception {
    Map<String, Ref> remoteRefs = getRemoteRefs(root, db, s);
    Ref remoteRef = remoteRefs.get(refName);
    if (remoteRef == null) {
      LOG.debug("Remote ref updated: repository " + LogUtil.describe(root) + ", ref '" + refName + "' no remote revision found");
      return true;
    }

    String cachedCurrentVersion = getCachedCurrentVersion(s.getRepositoryDir(), s.getRef());
    if (cachedCurrentVersion == null) {
      LOG.debug("Remote ref updated: repository " + LogUtil.describe(root) + ", ref '" + refName + "' local revision not found, remote revision " + remoteRef.getObjectId().name());
      return true;
    }

    String sha = GitUtils.versionRevision(cachedCurrentVersion);
    if (!remoteRef.getObjectId().name().equals(sha)) {
      LOG.debug("Remote ref updated: repository " + LogUtil.describe(root) + ", ref '" + refName + "' local revision " + sha + ", remote revision " + remoteRef.getObjectId().name());
      return true;
    }

    return false;
  }


  /**
   * Return cached current version for branch in repository in specified dir, or null if no cache version found.
   * @param repositoryDir cloned repository dir
   * @param branchName branch name of interest
   * @return see above
   */
  private String getCachedCurrentVersion(File repositoryDir, String branchName) {
    return myCurrentVersionCache.get(Pair.create(repositoryDir, branchName));
  }

  /**
   * Save current version for branch of repository in cache.
   * @param repositoryDir cloned repository dir
   * @param branchName branch name of interest
   * @param currentVersion current branch revision
   */
  private void setCachedCurrentVersion(File repositoryDir, String branchName, String currentVersion) {
    myCurrentVersionCache.put(Pair.create(repositoryDir, branchName), currentVersion);
  }


  /**
   * Resets all caches for current versions of branches for specified mirror dir
   * @param mirrorDir mirror dir of interest
   */
  public void resetCachedCurrentVersions(@NotNull File mirrorDir) {
    synchronized (myCurrentVersionCache) {
      for (Pair<File, String> k : myCurrentVersionCache.keySet()) {
        File dir = k.first;
        if (mirrorDir.equals(dir))
          myCurrentVersionCache.remove(k);
      }
    }
  }


  public boolean sourcesUpdatePossibleIfChangesNotFound(@NotNull VcsRoot root) {
    return true;
  }

  /**
   * Fetch data for the branch
   *
   * @param settings   settings for the root
   * @param repository the repository
   * @throws Exception if there is a problem with fetching data
   */
  private void fetchBranchData(Settings settings, Repository repository) throws Exception {
    final String refName = GitUtils.expandRef(settings.getRef());
    RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
    fetch(repository, settings.getRepositoryFetchURL(), spec, settings.getAuthSettings());
  }


  public void fetch(Repository db, URIish fetchURI, Collection<RefSpec> refspecs, Settings.AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
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
  public void fetch(Repository db, URIish fetchURI, RefSpec refspec, Settings.AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
    fetch(db, fetchURI, Collections.singletonList(refspec), auth);
  }


  public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
    OperationContext context = createContext(vcsRoot, "connection test");
    TestConnectionCommand command = new TestConnectionCommand(myTransportFactory, myRepositoryManager);
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

  public LabelingSupport getLabelingSupport() {
    return this;
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return this;
  }

  @NotNull
  public CollectChangesPolicy getCollectChangesPolicy() {
    return this;
  }

  @NotNull
  public BuildPatchPolicy getBuildPatchPolicy() {
    return this;
  }

  public String label(@NotNull String label, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules)
    throws VcsException {
    OperationContext context = createContext(root, "labelling");
    Settings s = context.getSettings();
    try {
      Repository r = context.getRepository();
      String commitSHA = GitUtils.versionRevision(version);
      RevCommit commit = ensureRevCommitLoaded(context, s, commitSHA);
      Git git = new Git(r);
      git.tag().setTagger(s.getTagger(r)).setName(label).setObjectId(commit).call();
      String tagRef = GitUtils.tagName(label);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Tag created  " + label + "=" + version + " for " + s.debugInfo());
      }
      synchronized (myRepositoryManager.getWriteLock(s.getRepositoryDir())) {
        final Transport tn = myTransportFactory.createTransport(r, s.getRepositoryPushURL(), s.getAuthSettings());
        try {
          final PushConnection c = tn.openPush();
          try {
            RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef, tagRef, false, null, null);
            c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef, ru));
            LOG.info("Tag  " + label + "=" + version + " pushed with status " + ru.getStatus() + " for " + s.debugInfo());
            switch (ru.getStatus()) {
              case UP_TO_DATE:
              case OK:
                break;
              default:
                throw new VcsException("The remote tag was not created (" + ru.getStatus() + "): " + label);
            }
          } finally {
            c.close();
          }
          return label;
        } finally {
          tn.close();
        }
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }

  /**
   * Get repository from tree walker
   *
   * @param r   the initial repository
   * @param tw  the tree walker
   * @param nth the position
   * @return the actual repository
   */
  private Repository getRepository(Repository r, TreeWalk tw, int nth) {
    Repository objRep;
    AbstractTreeIterator ti = tw.getTree(nth, AbstractTreeIterator.class);
    if (ti instanceof SubmoduleAwareTreeIterator) {
      objRep = ((SubmoduleAwareTreeIterator)ti).getRepository();
    } else {
      objRep = r;
    }
    return objRep;
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
      return new GitMapFullPath(context, this, rootEntry, fullPath).mapFullPath();
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


  public List<String> getRemoteBranches(@NotNull final VcsRoot root, @NotNull final String pattern) throws VcsException {
    Collection<Ref> remotes = getRemoteRefs(root).values();
    Pattern p = Pattern.compile(pattern);
    List<String> result = new ArrayList<String>();
    for (Ref ref : remotes) {
      if (p.matcher(ref.getName()).matches()) {
        result.add(ref.getName());
      }
    }
    return result;
  }

  @NotNull
  private Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "list remote refs");
    Settings s = context.getSettings();
    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("git-ls-remote", "");
      s.setUserDefinedRepositoryPath(tmpDir);
      Repository db = context.getRepository();
      return getRemoteRefs(root, db, s);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
      if (tmpDir != null) {
        myRepositoryManager.cleanLocksFor(tmpDir);
        FileUtil.delete(tmpDir);
      }
    }
  }


  @NotNull
  private Map<String, Ref> getRemoteRefs(@NotNull final VcsRoot root, @NotNull Repository db, @NotNull Settings s) throws Exception {
    final long start = System.currentTimeMillis();
    Transport transport = null;
    FetchConnection connection = null;
    try {
      transport = myTransportFactory.createTransport(db, s.getRepositoryFetchURL(), s.getAuthSettings());
      connection = transport.openFetch();
      return connection.getRefsMap();
    } catch (NotSupportedException nse) {
      throw friendlyNotSupportedException(root, s, nse);
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

  public Collection<VcsClientMapping> getClientMapping(final @NotNull VcsRoot root, final @NotNull IncludeRule rule) throws VcsException {
    final OperationContext context = createContext(root, "client-mapping");
    try {
      Settings s = context.getSettings();
      URIish uri = s.getRepositoryFetchURL();
      return Collections.singletonList(new VcsClientMapping(String.format("|%s|%s", uri.toString(), rule.getFrom()), rule.getTo()));
    } finally {
      context.close();
    }
  }

  @Override
  public boolean isDAGBasedVcs() {
    return true;
  }

  @NotNull
  public VcsBrowser getBrowserForRoot(@NotNull VcsRoot root) throws BrowserException {
    OperationContext context = createContext(root, "list files");
    try {
      Settings s = context.getSettings();
      String currentVersion = getCachedCurrentVersion(s.getRepositoryDir(), s.getRef());
      if (currentVersion != null)
        return new GitBrowser(this, root, GitUtils.versionRevision(currentVersion));
      return new EmptyBrowser();
    } catch (VcsException e) {
      throw new BrowserException(e);
    } finally {
      context.close();
    }
  }

  @NotNull
  @Override
  public Map<String, String> getVcsRepositoryProperties(@NotNull VcsRoot root) throws VcsException {
    Map<String, String> properties = new HashMap<String, String>(root.getProperties());
    properties.remove(Constants.PUSH_URL);
    properties.remove(Constants.BRANCH_NAME);
    properties.remove(Constants.PATH);
    properties.remove(Constants.AGENT_GIT_PATH);
    properties.remove(Constants.AUTH_METHOD);
    properties.remove(Constants.USERNAME_STYLE);
    properties.remove(Constants.IGNORE_KNOWN_HOSTS);
    properties.remove(Constants.PRIVATE_KEY_PATH);
    properties.remove(Constants.USERNAME);
    properties.remove(Constants.PASSWORD);
    properties.remove(Constants.PASSPHRASE);
    properties.remove(Constants.USERNAME_FOR_TAGS);
    return properties;
  }
}
