/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
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
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsPersonalSupport, LabelingSupport, VcsFileContentProvider, CollectChangesByCheckoutRules, BuildPatchByCheckoutRules,
             TestConnectionSupport, BranchSupport, IncludeRuleBasedMappingProvider {

  private static Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  /**
   * Random number generator used to generate artificial versions
   */
  private final Random myRandom = new Random();
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
  }


  private void setStreamFileThreshold() {
    WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setStreamFileThreshold(myConfig.getStreamFileThreshold() * WindowCacheConfig.MB);
    WindowCache.reconfigure(cfg);
  }


  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    List<ModificationData> result = new ArrayList<ModificationData>();
    OperationContext context = createContext(root, "collecting changes");
    try {
      Repository r = context.getRepository();
      RevWalk revs = new RevWalk(r);
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Collecting changes " + fromVersion + ".." + currentVersion + " for " + context.getSettings().debugInfo());
        }
        final String current = GitUtils.versionRevision(currentVersion);
        ensureRevCommitLoaded(context, context.getSettings(), current);
        final String from = GitUtils.versionRevision(fromVersion);
        final RevCommit currentRev = revs.parseCommit(ObjectId.fromString(current));
        revs.markStart(currentRev);
        revs.sort(RevSort.TOPO);
        final ObjectId fromId = ObjectId.fromString(from);
        if (r.hasObject(fromId)) {
          final RevCommit fromRev = revs.parseCommit(fromId);
          String firstUninterestingVersion = GitServerUtil.makeVersion(fromRev);
          revs.markUninteresting(fromRev);
          RevCommit c;
          boolean lastCommit = true;
          while ((c = revs.next()) != null) {
            result.add(createModificationData(context, c, r, !lastCommit, firstUninterestingVersion, checkoutRules));
            lastCommit = false;
          }
        } else {
          LOG.warn("From version " + fromVersion + " is not found, collecting changes based on commit date and time " + context.getSettings().debugInfo());
          RevCommit c;
          long limitTime = GitUtils.versionTime(fromVersion);
          boolean lastCommit = true;
          while ((c = revs.next()) != null) {
            if (c.getCommitTime() * 1000L <= limitTime) {
              revs.markUninteresting(c);
            } else {
              result.add(createModificationData(context, c, r, !lastCommit, null, checkoutRules));
            }
            lastCommit = false;
          }
          // add revision with warning text and random number as version
          byte[] idBytes = new byte[20];
          myRandom.nextBytes(idBytes);
          String version = GitUtils.makeVersion(ObjectId.fromRaw(idBytes).name(), limitTime);
          result.add(new ModificationData(new Date(currentRev.getCommitTime()),
                                      new ArrayList<VcsChange>(),
                                      "The previous version was removed from repository, " +
                                      "getting changes using date. The changes reported might be not accurate.",
                                      GitServerUtil.SYSTEM_USER,
                                      root,
                                      version,
                                      GitServerUtil.displayVersion(version)));
        }
      } finally {
        revs.release();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
    return result;
  }


  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }

  @NotNull
  public Map<String, String> getBranchesRevisions(@NotNull VcsRoot root) throws VcsException {
    final Map<String, String> result = new HashMap<String, String>();
    for (Ref ref : getRemoteRefs(root).values()) {
      result.put(ref.getName(), ref.getObjectId().name());
    }
    return result;
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

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot originalRoot,
                                               @NotNull String  originalRootVersion,
                                               @NotNull VcsRoot branchRoot,
                                               @Nullable String  branchRootVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    LOG.debug("Collecting changes [" +LogUtil.describe(originalRoot) + "-" + originalRootVersion + "].." +
              "[" + LogUtil.describe(branchRoot) + "-" + branchRootVersion + "]");
    String forkPoint = getLastCommonVersion(originalRoot, originalRootVersion, branchRoot, branchRootVersion);
    return collectChanges(branchRoot, forkPoint, branchRootVersion, checkoutRules);
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

  private ModificationData createModificationData(final OperationContext context,
                                                  final RevCommit commit,
                                                  final Repository db,
                                                  final boolean ignoreSubmodulesErrors,
                                                  final String firstUninterestingVersion,
                                                  final CheckoutRules checkoutRules) throws IOException, VcsException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Collecting changes in commit " + commit.getId().name() + ":" + commit.getShortMessage() +
                " (" + commit.getCommitterIdent().getWhen() + ") for " + context.getSettings().debugInfo());
    }
    String currentVersion = GitServerUtil.makeVersion(commit);
    String parentVersion = GitServerUtil.getParentVersion(commit, firstUninterestingVersion);
    List<VcsChange> changes = getCommitChanges(context, db, commit, currentVersion, parentVersion, ignoreSubmodulesErrors);
    ModificationData result = new ModificationData(commit.getAuthorIdent().getWhen(), changes, commit.getFullMessage(),
                                                   GitServerUtil.getUser(context.getSettings(), commit), context.getRoot(), currentVersion, commit.getId().name());
    if (isMergeCommit(commit) && changes.isEmpty()) {
      boolean hasInterestingChanges = hasInterestingChanges(context, db, commit, ignoreSubmodulesErrors, checkoutRules, GitUtils.versionRevision(firstUninterestingVersion));
      if (hasInterestingChanges) {
        result.setCanBeIgnored(false);
      }
    }
    return result;
  }

  private boolean isMergeCommit(RevCommit commit) {
    return commit.getParents().length > 1;
  }

  private boolean hasInterestingChanges(final OperationContext context,
                                        final Repository db,
                                        final RevCommit mergeCommit,
                                        final boolean ignoreSubmodulesErrors,
                                        final CheckoutRules rules,
                                        final String firstUninterestingSHA)
    throws IOException, VcsException {
    RevWalk walk = new RevWalk(db);
    List<RevCommit> start = new ArrayList<RevCommit>();
    for (RevCommit c : mergeCommit.getParents()) {
      start.add(walk.parseCommit(c));
    }
    walk.markStart(start);
    walk.markUninteresting(walk.parseCommit(ObjectId.fromString(firstUninterestingSHA)));
    walk.sort(RevSort.TOPO);
    try {
      RevCommit c;
      while ((c = walk.next()) != null) {
        TreeWalk tw = new TreeWalk(db);
        tw.setRecursive(true);
        tw.setFilter(TreeFilter.ANY_DIFF);
        try {
          context.addTree(tw, db, c, ignoreSubmodulesErrors);
          tw.addTree(c.getTree().getId());
          for (RevCommit parent : c.getParents()) {
            context.addTree(tw, db, parent, ignoreSubmodulesErrors);
          }
          while (tw.next()) {
            String path = tw.getPathString();
            if (rules.shouldInclude(path)) {
              return true;
            }
          }
        } finally {
          tw.release();
        }
      }
    } finally {
      walk.release();
    }
    return false;
  }

  /**
   * Get changes for the commit
   *
   * @param context context of current operation
   * @param r repository
   * @param commit current commit
   * @param currentVersion teamcity version of current commit (sha@time)
   * @param parentVersion parent version to use in VcsChange objects
   * @param ignoreSubmodulesErrors should method ignore errors in submodules or not
   * @return the commit changes
   * @throws IOException
   * @throws VcsException
   */
  private List<VcsChange> getCommitChanges(final OperationContext context,
                                           final Repository r,
                                           final RevCommit commit,
                                           final String currentVersion,
                                           final String parentVersion,
                                           final boolean ignoreSubmodulesErrors) throws IOException, VcsException {
    List<VcsChange> changes = new ArrayList<VcsChange>();
    TreeWalk tw = new TreeWalk(r);
    try {
      IgnoreSubmoduleErrorsTreeFilter filter = new IgnoreSubmoduleErrorsTreeFilter(context.getSettings());
      tw.setFilter(filter);
      tw.setRecursive(true);
      context.addTree(tw, r, commit, ignoreSubmodulesErrors);
      for (RevCommit parentCommit : commit.getParents()) {
        context.addTree(tw, r, parentCommit, true);
      }
      String repositoryDebugInfo = context.getSettings().debugInfo();
      RevCommit commitWithFix = null;
      Map<String, RevCommit> commitsWithFix = new HashMap<String, RevCommit>();
      while (tw.next()) {
        String path = tw.getPathString();
        if (context.getSettings().isCheckoutSubmodules()) {
          if (filter.isBrokenSubmoduleEntry(path)) {
            commitWithFix = getPreviousCommitWithFixedSubmodule(context, r, commit, path);
            commitsWithFix.put(path, commitWithFix);
            if (commitWithFix != null) {
              TreeWalk tw2 = new TreeWalk(r);
              try {
                tw2.setFilter(TreeFilter.ANY_DIFF);
                tw2.setRecursive(true);
                context.addTree(tw2, r, commit, true);
                context.addTree(tw2, r, commitWithFix, true);
                while (tw2.next()) {
                  if (tw2.getPathString().equals(path)) {
                    addVcsChange(changes, currentVersion, GitServerUtil.makeVersion(commitWithFix), tw2, repositoryDebugInfo, path);
                  }
                }
              } finally {
                tw2.release();
              }
            } else {
              addVcsChange(changes, currentVersion, parentVersion, tw, repositoryDebugInfo, path);
            }
          } else if (filter.isChildOfBrokenSubmoduleEntry(path)) {
            String brokenSubmodulePath = filter.getSubmodulePathForChildPath(path);
            commitWithFix = commitsWithFix.get(brokenSubmodulePath);
            if (commitWithFix != null) {
              TreeWalk tw2 = new TreeWalk(r);
              try {
                tw2.setFilter(TreeFilter.ANY_DIFF);
                tw2.setRecursive(true);
                context.addTree(tw2, r, commit, true);
                context.addTree(tw2, r, commitWithFix, true);
                while (tw2.next()) {
                  if (tw2.getPathString().equals(path)) {
                    addVcsChange(changes, currentVersion, GitServerUtil.makeVersion(commitWithFix), tw2, repositoryDebugInfo, path);
                  }
                }
              } finally {
                tw2.release();
              }
            } else {
              addVcsChange(changes, currentVersion, parentVersion, tw, repositoryDebugInfo, path);
            }
          } else {
            addVcsChange(changes, currentVersion, parentVersion, tw, repositoryDebugInfo, path);
          }
        } else {
          addVcsChange(changes, currentVersion, parentVersion, tw, repositoryDebugInfo, path);
        }
      }
      return changes;
    } finally {
      tw.release();
    }
  }

  private void addVcsChange(List<VcsChange> changes, String currentVersion, String parentVersion, TreeWalk tw, String repositoryDebugInfo, String path) {
    VcsChange change = getVcsChange(tw, path, currentVersion, parentVersion, repositoryDebugInfo);
    if (change != null)
      changes.add(change);
  }

  private VcsChange getVcsChange(TreeWalk treeWalk, String path, String commitSHA, String parentCommitSHA, String repositoryDebugInfo) {
    final ChangeType gitChangeType = classifyChange(treeWalk);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing change " + treeWalkInfo(treeWalk) + " as " + gitChangeType + " " + repositoryDebugInfo);
    }
    VcsChange.Type type = getChangeType(gitChangeType, treeWalk, path);
    if (type == VcsChange.Type.NOT_CHANGED) {
      return null;
    } else {
      String description = gitChangeType == ChangeType.FILE_MODE_CHANGED ? "File mode changed" : null;
      return new VcsChange(type, description, path, path, parentCommitSHA, commitSHA);
    }
  }

  private VcsChange.Type getChangeType(ChangeType gitChangeType, TreeWalk treeWalk, String path) {
    switch (gitChangeType) {
      case UNCHANGED:
        return VcsChange.Type.NOT_CHANGED;
      case ADDED:
        return VcsChange.Type.ADDED;
      case DELETED:
        TreeFilter filter = treeWalk.getFilter();
        if (filter instanceof IgnoreSubmoduleErrorsTreeFilter && ((IgnoreSubmoduleErrorsTreeFilter) treeWalk.getFilter()).getBrokenSubmodulePathsInRestTrees().contains(path)) {
          return VcsChange.Type.NOT_CHANGED;
        } else {
          return VcsChange.Type.REMOVED;
        }
      case MODIFIED:
        return VcsChange.Type.CHANGED;
      case FILE_MODE_CHANGED:
        return VcsChange.Type.CHANGED;
      default:
        throw new IllegalStateException("Unknown change type");
    }
  }

  private RevCommit getPreviousCommitWithFixedSubmodule(OperationContext context, Repository db, RevCommit fromCommit, String submodulePath)
    throws IOException, VcsException {
    int searchDepth = myConfig.getFixedSubmoduleCommitSearchDepth();
    if (searchDepth == 0)
      return null;

    RevWalk revWalk = new RevWalk(db);
    try {
      final RevCommit fromRev = revWalk.parseCommit(fromCommit.getId());
      revWalk.markStart(fromRev);
      revWalk.sort(RevSort.TOPO);

      RevCommit result = null;
      RevCommit prevRev;
      revWalk.next();
      int depth = 0;
      while (result == null && depth < searchDepth && (prevRev = revWalk.next()) != null) {
        depth++;
        TreeWalk prevTreeWalk = new TreeWalk(db);
        try {
          prevTreeWalk.setFilter(TreeFilter.ALL);
          prevTreeWalk.setRecursive(true);
          context.addTree(prevTreeWalk, db, prevRev, true, false);
          while(prevTreeWalk.next()) {
            if (prevTreeWalk.getPathString().startsWith(submodulePath)) {
              SubmoduleAwareTreeIterator iter = prevTreeWalk.getTree(0, SubmoduleAwareTreeIterator.class);
              if (!iter.isSubmoduleError() && iter.getParent().isOnSubmodule()) {
                result = prevRev;
                break;
              }
            }
          }
        } finally {
          prevTreeWalk.release();
        }
      }
      return result;
    } finally {
      revWalk.release();
    }
  }

  /**
   * Classify change in tree walker. The first tree is assumed to be a current commit and other
   * trees are assumed to be parent commits. In the case of multiple changes, the changes that
   * come from at lease one parent commit are assumed to be reported in the parent commit.
   *
   * @param tw     tree walker to examine
   * @return change type
   */
  @NotNull
  private static ChangeType classifyChange(@NotNull TreeWalk tw) {
    final FileMode mode0 = tw.getFileMode(0);
    if (FileMode.MISSING.equals(mode0)) {
      for (int i = 1; i < tw.getTreeCount(); i++) {
        if (FileMode.MISSING.equals(tw.getFileMode(i))) {
          // the delete merge
          return ChangeType.UNCHANGED;
        }
      }
      return ChangeType.DELETED;
    }
    boolean fileAdded = true;
    for (int i = 1; i < tw.getTreeCount(); i++) {
      if (!FileMode.MISSING.equals(tw.getFileMode(i))) {
        fileAdded = false;
        break;
      }
    }
    if (fileAdded) {
      return ChangeType.ADDED;
    }
    boolean fileModified = true;
    for (int i = 1; i < tw.getTreeCount(); i++) {
      if (tw.idEqual(0, i)) {
        fileModified = false;
        break;
      }
    }
    if (fileModified) {
      return ChangeType.MODIFIED;
    }
    int modeBits0 = mode0.getBits();
    boolean fileModeModified = true;
    for (int i = 1; i < tw.getTreeCount(); i++) {
      int modeBits = tw.getFileMode(i).getBits();
      if (modeBits == modeBits0) {
        fileModeModified = false;
        break;
      }
    }
    if (fileModeModified) {
      return ChangeType.FILE_MODE_CHANGED;
    }
    return ChangeType.UNCHANGED;
  }

  public void buildPatch(@NotNull VcsRoot root,
                         @Nullable final String fromVersion,
                         @NotNull String toVersion,
                         @NotNull final PatchBuilder builder,
                         @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
    final OperationContext context = createContext(root, "patch building");
    final boolean debugFlag = LOG.isDebugEnabled();
    final boolean debugInfoOnEachCommit = myConfig.isPrintDebugInfoOnEachCommit();
    try {
      final Repository r = context.getRepository();
      TreeWalk tw = null;
      try {
        RevCommit toCommit = ensureRevCommitLoaded(context, context.getSettings(), GitUtils.versionRevision(toVersion));
        if (toCommit == null) {
          throw new VcsException("Missing commit for version: " + toVersion);
        }
        tw = new TreeWalk(r);
        tw.setFilter(TreeFilter.ANY_DIFF);
        tw.setRecursive(true);
        context.addTree(tw, r, toCommit, false);
        if (fromVersion != null) {
          if (debugFlag) {
            LOG.debug("Creating patch " + fromVersion + ".." + toVersion + " for " + context.getSettings().debugInfo());
          }
          RevCommit fromCommit = getCommit(r, GitUtils.versionRevision(fromVersion));
          if (fromCommit == null) {
            throw new IncrementalPatchImpossibleException("The form commit " + fromVersion + " is not available in the repository");
          }
          context.addTree(tw, r, fromCommit, true);
        } else {
          if (debugFlag) {
            LOG.debug("Creating clean patch " + toVersion + " for " + context.getSettings().debugInfo());
          }
          tw.addTree(new EmptyTreeIterator());
        }
        final List<Callable<Void>> actions = new LinkedList<Callable<Void>>();
        while (tw.next()) {
          final String path = tw.getPathString();
          final String mapped = checkoutRules.map(path);
          if (mapped == null) {
            continue;
          }
          if (debugFlag && debugInfoOnEachCommit) {
            LOG.debug("File found " + treeWalkInfo(tw) + " for " + context.getSettings().debugInfo());
          }
          switch (classifyChange(tw)) {
            case UNCHANGED:
              // change is ignored
              continue;
            case MODIFIED:
            case ADDED:
            case FILE_MODE_CHANGED:
              if (!FileMode.GITLINK.equals(tw.getFileMode(0))) {
                final String mode = getModeDiff(tw);
                final ObjectId id = tw.getObjectId(0);
                final Repository objRep = getRepository(r, tw, 0);
                final Callable<Void> action = new Callable<Void>() {
                  public Void call() throws Exception {
                    InputStream objectStream = null;
                    try {
                      final ObjectLoader loader = objRep.open(id);
                      if (loader == null) {
                        throw new IOException("Unable to find blob " + id + (path == null ? "" : "(" + path + ")") + " in repository " + r);
                      }
                      objectStream = loader.isLarge() ? loader.openStream() : new ByteArrayInputStream(loader.getCachedBytes());
                      builder.changeOrCreateBinaryFile(GitUtils.toFile(mapped), mode, objectStream, loader.getSize());
                    } catch (Error e) {
                      LOG.error("Unable to load file: " + path + "(" + id.name() + ") from: " + context.getSettings().debugInfo());
                      throw e;
                    } catch (Exception e) {
                      LOG.error("Unable to load file: " + path + "(" + id.name() + ") from: " + context.getSettings().debugInfo());
                      throw e;
                    } finally {
                      if (objectStream != null) objectStream.close();
                    }
                    return null;
                  }
                };
                if (fromVersion == null) {
                  // clean patch, we aren't going to see any deletes
                  action.call();
                } else {
                  actions.add(action);
                }
              }
              break;
            case DELETED:
              if (!FileMode.GITLINK.equals(tw.getFileMode(0))) {
                builder.deleteFile(GitUtils.toFile(mapped), true);
              }
              break;
            default:
              throw new IllegalStateException("Unknown change type");
          }
        }
        for (Callable<Void> a : actions) {
          a.call();
        }
      } finally {
        if (tw != null) tw.release();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }

  /**
   * Get debug info for treewalk (used in logging)
   *
   * @param tw tree walk object
   * @return debug info about tree walk
   */
  private static String treeWalkInfo(TreeWalk tw) {
    StringBuilder b = new StringBuilder();
    b.append(tw.getPathString());
    b.append('(');
    final int n = tw.getTreeCount();
    for (int i = 0; i < n; i++) {
      if (i != 0) {
        b.append(", ");
      }
      b.append(tw.getObjectId(i).name());
      b.append(String.format("%04o", tw.getFileMode(i).getBits()));
    }
    b.append(')');
    return b.toString();
  }

  /**
   * Get difference in the file mode (passed to chmod), null if there is no difference
   *
   * @param tw the tree walker to check
   * @return the mode difference or null if there is no different
   */
  private static String getModeDiff(TreeWalk tw) {
    boolean cExec = isExecutable(tw.getFileMode(0));
    boolean pExec = isExecutable(tw.getFileMode(1));
    String mode;
    if (cExec & !pExec) {
      mode = "a+x";
    } else if (!cExec & pExec) {
      mode = "a-x";
    } else {
      mode = null;
    }
    if (mode != null && LOG.isDebugEnabled()) {
      LOG.debug("The mode change " + mode + " is detected for " + treeWalkInfo(tw));
    }
    return mode;
  }

  /**
   * Check if the file mode is executable
   *
   * @param m file mode to check
   * @return true if the file is executable
   */
  private static boolean isExecutable(FileMode m) {
    return (m.getBits() & (1 << 6)) != 0;
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

  private RevCommit ensureCommitLoaded(OperationContext context, Settings rootSettings, String commitWithDate) throws Exception {
    final String commit = GitUtils.versionRevision(commitWithDate);
    return ensureRevCommitLoaded(context, rootSettings, commit);
  }

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

  RevCommit getCommit(Repository repository, String commitSHA) throws IOException {
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
    Settings s = context.getSettings();
    try {
      Repository r = context.getRepository();
      String refName = GitUtils.expandRef(s.getRef());

      if (isRemoteRefUpdated(root, r, s, refName))
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
    if (remoteRef == null)
      return true;

    String cachedCurrentVersion = getCachedCurrentVersion(s.getRepositoryDir(), s.getRef());
    return cachedCurrentVersion == null || !remoteRef.getObjectId().name().equals(GitUtils.versionRevision(cachedCurrentVersion));
  }


  /**
   * Return cached current version for branch in repository in specified dir, or null if no cache version found.
   */
  private String getCachedCurrentVersion(File repositoryDir, String branchName) {
    return myCurrentVersionCache.get(Pair.create(repositoryDir, branchName));
  }

  /**
   * Save current version for branch of repository in cache.
   */
  private void setCachedCurrentVersion(File repositoryDir, String branchName, String currentVersion) {
    myCurrentVersionCache.put(Pair.create(repositoryDir, branchName), currentVersion);
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
      git.tag().setName(label).setObjectId(commit).call();
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
      if (connection != null) connection.close();
      if (transport != null) transport.close();
    }
  }

  /** Git change type */
  private enum ChangeType {
    /** the file is added */
    ADDED,
    /** the file is deleted */
    DELETED,
    /** the file content (or content+mode) changed */
    MODIFIED,
    /** the file mode only changed */
    FILE_MODE_CHANGED,
    /** no change detected */
    UNCHANGED,
  }

  @NotNull
  private ObjectId getVcsRootGitId(final @NotNull VcsRoot root) throws VcsException{
    final OperationContext context = createContext(root, "client-mapping");
    try {
      final Settings gitSettings = context.getSettings(root);
      final Repository gitRepo = context.getRepository(gitSettings);
      if(gitRepo == null){
        throw new VcsException(String.format("Could not find Git Repository for '%s'", root.getName()));
      }
      final ObjectId objectId = gitRepo.resolve(gitSettings.getRef());
      if(objectId == null){
        throw new VcsException(String.format("Could not resolve Git Reference '%s'", gitSettings.getRef()));
      }
      return objectId;
    } catch (AmbiguousObjectException e) {
      throw new VcsException(e);
    } catch (IOException e) {
      throw new VcsException(e);
    } finally {
      context.close();
    }
  }

  public Collection<VcsClientMapping> getClientMapping(final @NotNull VcsRoot root, final @NotNull IncludeRule rule) throws VcsException {
    final ObjectId gitObjId = getVcsRootGitId(root);
    return Collections.singletonList(new VcsClientMapping(String.format("%s||%s", gitObjId.name(), rule.getFrom()), rule.getTo()));
  }
}
