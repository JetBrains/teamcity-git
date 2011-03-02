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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PasswordSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PrivateKeyFileSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.RefreshableSshConfigSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.RecentEntriesCache;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.apache.commons.codec.Decoder;
import org.eclipse.jgit.JGitText;
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
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements VcsPersonalSupport, LabelingSupport, VcsFileContentProvider, CollectChangesByCheckoutRules, BuildPatchByCheckoutRules,
             TestConnectionSupport, BranchSupport {

  private static Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  private static Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");
  /**
   * Random number generator used to generate artificial versions
   */
  private final Random myRandom = new Random();
  /**
   * JGit operations locks (repository dir -> lock)
   *
   * Due to problems with concurrency in jgit fetch and push operations are synchronized by locks from this map.
   *
   * These locks are also used in Cleaner
   */
  private final ConcurrentMap<File, Object> myRepositoryLocks = new ConcurrentHashMap<File, Object>();
  /**
   * Current version cache (Pair<bare repository dir, branch name> -> current version).
   */
  private final RecentEntriesCache<Pair<File, String>, String> myCurrentVersionCache;
  private final ServerPaths myServerPaths;
  private final File myCacheDir;
  /**
   * The default SSH session factory used for not explicitly configured host
   * It fails when user is prompted for some information.
   */
  private final RefreshableSshConfigSessionFactory mySshSessionFactory;
  /**
   * This factory is used when known host database is specified to be ignored
   */
  private final RefreshableSshConfigSessionFactory mySshSessionFactoryKnownHostsIgnored;

  private final ExtensionHolder myExtensionHolder;
  private volatile String myDisplayName = null;


  public GitVcsSupport(@NotNull  final ServerPaths serverPaths,
                       @Nullable final ExtensionHolder extensionHolder,
                       @Nullable final EventDispatcher<BuildServerListener> dispatcher) {
    myServerPaths = serverPaths;
    myCacheDir = new File(myServerPaths.getCachesDir(), "git");
    myExtensionHolder = extensionHolder;
    int currentVersionCacheSize = TeamCityProperties.getInteger("teamcity.git.current.version.cache.size", 100);
    myCurrentVersionCache = new RecentEntriesCache<Pair<File, String>, String>(currentVersionCacheSize);
    final boolean monitorSshConfigs = dispatcher != null; //dispatcher is null in tests and when invoked from the Fetcher
    mySshSessionFactory = new RefreshableSshConfigSessionFactory(monitorSshConfigs);
    mySshSessionFactoryKnownHostsIgnored = new RefreshableSshConfigSessionFactory(monitorSshConfigs) {
      // note that different instance is used because JSch cannot be shared with strict host checking
      public Session getSession(String user, String pass, String host, int port, CredentialsProvider credentialsProvider, FS fs) throws JSchException {
        final Session session = super.getSession(user, pass, host, port, credentialsProvider, fs);
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
      }
    };
    setStreamFileThreshold();
    if (monitorSshConfigs) {
      dispatcher.addListener(new BuildServerAdapter() {
        @Override
        public void serverShutdown() {
          mySshSessionFactory.stopMonitoringConfigs();
          mySshSessionFactoryKnownHostsIgnored.stopMonitoringConfigs();
        }
      });
    }
  }

  private void setStreamFileThreshold() {
    WindowCacheConfig cfg = new WindowCacheConfig();
    if (separateProcessForFetch()) {
      cfg.setStreamFileThreshold(TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", 128) * WindowCacheConfig.MB);
    } else {
      cfg.setStreamFileThreshold(TeamCityProperties.getInteger("teamcity.git.stream.file.threshold.mb", 64) * WindowCacheConfig.MB);
    }
    WindowCache.reconfigure(cfg);
  }


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
        revs.sort(RevSort.COMMIT_TIME_DESC);
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
        context.close();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    }
    return result;
  }


  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/remote-run/TEAMCITY_USERNAME/TOPIC";
  }

  @NotNull
  public Map<String, String> getBranchesRevisions(@NotNull VcsRoot root) throws VcsException {
    final Map<String, String> result = new HashMap<String, String>();
    for (Ref ref : getRemoteRefs(root)) {
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

  public List<ModificationData> collectChanges(@NotNull VcsRoot originalRoot,
                                               @NotNull String  originalRootVersion,
                                               @NotNull VcsRoot branchRoot,
                                               @Nullable String  branchRootVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    if (LOG.isDebugEnabled()) {
      OperationContext context = createContext(branchRoot, "collecting changes");
      Settings originalSettings = context.getSettings(originalRoot);
      Settings branchSettings = context.getSettings();
      LOG.debug("Collecting changes [" + originalSettings.debugInfo() + "-" + originalRootVersion + "]..[" + branchSettings.debugInfo() + "-" + branchRootVersion + "]");
    }
    String forkPoint = getLastCommonVersion(originalRoot, originalRootVersion, branchRoot, branchRootVersion);
    return collectChanges(branchRoot, forkPoint, branchRootVersion, checkoutRules);
  }

  private String getLastCommonVersion(VcsRoot baseRoot, String baseVersion, VcsRoot tipRoot, String tipVersion) throws VcsException {
    OperationContext context = createContext(tipRoot, "find fork version");
    Settings baseSettings = context.getSettings(baseRoot);
    Settings tipSettings = context.getSettings();
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
      if (LOG.isDebugEnabled()) {
        LOG.debug("Last common revision between " + baseSettings.debugInfo() + " and " + tipSettings.debugInfo() + " is " + result);
      }
      return result;
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      if (walk != null) walk.release();
      context.close();
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
        tw.reset();
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
   * @param r            the change version
   * @param commit           the current commit
   * @param currentVersion           the commit version
   * @param parentVersion           the parent commit version
   * @return the commit changes
   * @throws IOException if there is a repository access problem
   */
  private List<VcsChange> getCommitChanges(final OperationContext context,
                                           final Repository r,
                                           final RevCommit commit,
                                           final String currentVersion,
                                           final String parentVersion,
                                           final boolean ignoreSubmodulesErrors)
    throws IOException, VcsException {
    List<VcsChange> changes = new ArrayList<VcsChange>();
    TreeWalk tw = new TreeWalk(r);
    try {
      IgnoreSubmoduleErrorsTreeFilter filter = new IgnoreSubmoduleErrorsTreeFilter(context.getSettings());
      tw.setFilter(filter);
      tw.setRecursive(true);
      // remove empty tree iterator before adding new tree
      tw.reset();
      context.addTree(tw, r, commit, ignoreSubmodulesErrors);
      for (RevCommit parentCommit : commit.getParents()) {
        context.addTree(tw, r, parentCommit, true);
      }
      String repositoryDebugInfo = context.getSettings().debugInfo();
      while (tw.next()) {
        String path = tw.getPathString();
        RevCommit commitWithFix = null;
        if (context.getSettings().isCheckoutSubmodules() && filter.isBrokenSubmodulePath(path)
            && (commitWithFix = getPreviousCommitWithFixedSubmodule(context, r, commit, path)) != null) {
          //report changes between 2 commits where submodules fixed
          TreeWalk tw2 = new TreeWalk(r);
          try {
            tw2.setFilter(TreeFilter.ANY_DIFF);
            tw2.setRecursive(true);
            tw2.reset();
            context.addTree(tw2, r, commit, true);
            context.addTree(tw2, r, commitWithFix, true);
            while (tw2.next()) {
              if (tw2.getPathString().equals(path)) {
                VcsChange change = getVcsChange(tw2, path, currentVersion, commitWithFix.getId().name(), repositoryDebugInfo);
                if (change != null) {
                  changes.add(change);
                }
              }
            }
          } finally {
            tw2.release();
          }
        } else {
          VcsChange change = getVcsChange(tw, path, currentVersion, parentVersion, repositoryDebugInfo);
          if (change != null) {
            changes.add(change);
          }
        }
      }
    return changes;
    } finally {
      tw.release();
    }
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
        if (((IgnoreSubmoduleErrorsTreeFilter) treeWalk.getFilter()).getBrokenSubmodulePathsInRestTrees().contains(path)) {
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
    RevWalk revWalk = new RevWalk(db);
    try {
      final RevCommit fromRev = revWalk.parseCommit(fromCommit.getId());
      revWalk.markStart(fromRev);
      revWalk.sort(RevSort.TOPO);
      revWalk.sort(RevSort.COMMIT_TIME_DESC);

      RevCommit result = null;
      RevCommit prevRev;
      revWalk.next();
      while (result == null && (prevRev = revWalk.next()) != null) {
        TreeWalk prevTreeWalk = new TreeWalk(db);
        try {
          prevTreeWalk.setFilter(TreeFilter.ALL);
          prevTreeWalk.setRecursive(true);
          prevTreeWalk.reset();
          context.addTree(prevTreeWalk, db, prevRev, true);
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
    final boolean debugInfoOnEachCommit = TeamCityProperties.getBoolean("teamcity.git.commit.debug.info");
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
        tw.reset();
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
        context.close();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
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
        tw.reset();
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
        context.close();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
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
        throw new VcsException("The version name could not be resolved " + commitSHA + "(" + settings.getRepositoryFetchURL().toString() + "#" + settings.getBranch() + ")");
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
      fetchBranchData(s, r);
      String refName = GitUtils.branchRef(s.getBranch());
      Ref branchRef = r.getRef(refName);
      if (branchRef == null) {
        throw new VcsException("The branch name could not be resolved " + refName);
      }
      String cachedCurrentVersion = getCachedCurrentVersion(s.getRepositoryDir(), s.getBranch());
      if (cachedCurrentVersion != null && GitUtils.versionRevision(cachedCurrentVersion).equals(branchRef.getObjectId().name())) {
        return cachedCurrentVersion;
      } else {
        RevCommit c = getCommit(r, branchRef.getObjectId());
        if (c == null) {
          throw new VcsException("The branch name could not be resolved " + refName);
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Current version: " + c.getId().name() + " " + s.debugInfo());
        }
        final String currentVersion = GitServerUtil.makeVersion(c);
        setCachedCurrentVersion(s.getRepositoryDir(), s.getBranch(), currentVersion);
        GitMapFullPath.invalidateRevisionsCache(root);
        return currentVersion;
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
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
    final String refName = GitUtils.branchRef(settings.getBranch());
    RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
    fetch(repository, settings.getAuthSettings(), settings.getRepositoryFetchURL(), spec);
  }

  /**
   * Make fetch into local repository (it.s getDirectory() should be != null)
   *
   * @param db repository
   * @param auth auth settings
   * @param fetchURI uri to fetch
   * @param refspec refspec
   */
  public void fetch(Repository db, Settings.AuthSettings auth, URIish fetchURI, RefSpec refspec)
    throws NotSupportedException, VcsException, TransportException {
    File repositoryDir = db.getDirectory();
    assert repositoryDir != null : "Non-local repository";
    synchronized (getRepositoryLock(repositoryDir)) {
      if (separateProcessForFetch()) {
        fetchInSeparateProcess(db, auth, fetchURI, refspec);
      } else {
        fetchInSameProcess(db, auth, fetchURI, refspec);
      }
    }
  }

  /**
   * Get repository lock
   *
   * @param repositoryDir repository dir where fetch run
   * @return lock associated with repository dir
   */
  @NotNull
  public Object getRepositoryLock(@NotNull File repositoryDir) {
    Object newLock = new Object();
    Object existingLock = myRepositoryLocks.putIfAbsent(repositoryDir, newLock);
    if (existingLock != null)
      return existingLock;
    else
      return newLock;
  }

  private void fetchInSameProcess(final Repository db, final Settings.AuthSettings auth, final URIish uri, final RefSpec refSpec)
    throws NotSupportedException, VcsException, TransportException {
    final String debugInfo = " (" + (db.getDirectory() != null? db.getDirectory().getAbsolutePath() + ", ":"")
                             + uri.toString() + "#" + refSpec.toString() + ")";
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fetch in server process: " + debugInfo);
    }
    final long fetchStart = System.currentTimeMillis();
    final Transport tn = openTransport(auth, db, uri);
    try {
      tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(refSpec));
    } catch (OutOfMemoryError oom) {
      LOG.warn("There is not enough memory for git fetch, try to run fetch in a separate process.");
      clean(db);
    } finally {
      tn.close();
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug("[fetch in server process] root=" + debugInfo + ", took " + (System.currentTimeMillis() - fetchStart) + "ms");
      }
    }

  }

  /**
   * Fetch data for the branch in separate process
   *
   * @param settings   settings for the root
   * @param repository the repository
   * @throws Exception if there is a problem with fetching data
   */
  private void fetchInSeparateProcess(final Repository repository, final Settings.AuthSettings settings, final URIish uri, final RefSpec spec)
    throws VcsException {
    final long fetchStart = System.currentTimeMillis();
    final String debugInfo = " (" + (repository.getDirectory() != null? repository.getDirectory().getAbsolutePath() + ", ":"")
                             + uri.toString() + "#" + spec.toString() + ")";
    GeneralCommandLine cl = new GeneralCommandLine();
    cl.setWorkingDirectory(repository.getDirectory());
    cl.setExePath(getFetchProcessJavaPath());
    cl.addParameters("-Xmx" + getFetchProcessMaxMemory(), "-cp", getFetchClasspath(), Fetcher.class.getName(),
                     uri.toString());//last parameter is not used in Fetcher, but is useful to distinguish fetch processes
    if (LOG.isDebugEnabled()) {
      LOG.debug("Start fetch process for " + debugInfo);
    }
    final List<Exception> errors = new ArrayList<Exception>();
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, null, new SimpleCommandLineProcessRunner.RunCommandEvents() {
      public void onProcessStarted(Process ps) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Fetch process for " + debugInfo + " started");
        }
        OutputStream processInput = ps.getOutputStream();
        try {
          Map<String, String> properties = new HashMap<String, String>(settings.toMap());
          properties.put(Constants.REPOSITORY_DIR_PROPERTY_NAME, repository.getDirectory().getCanonicalPath());
          properties.put(Constants.FETCH_URL, uri.toString());
          properties.put(Constants.REFSPEC, spec.toString());
          properties.put(Constants.VCS_DEBUG_ENABLED, String.valueOf(Loggers.VCS.isDebugEnabled()));
          processInput.write(VcsRootImpl.propertiesToString(properties).getBytes("UTF-8"));
          processInput.flush();
        } catch (IOException e) {
          errors.add(e);
        } finally {
          try {
            processInput.close();
          } catch (IOException e) {
            //ignore
          }
        }
      }

      public void onProcessFinished(Process ps) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Fetch process for " + debugInfo + " finished");
        }
      }

      public Integer getOutputIdleSecondsTimeout() {
        return getFetchTimeout();
      }
    });

    if (PERFORMANCE_LOG.isDebugEnabled()) {
      PERFORMANCE_LOG.debug("[fetch in separate process] root=" + debugInfo + ", took " + (System.currentTimeMillis() - fetchStart) + "ms");
    }
    if (!errors.isEmpty()) {
      throw new VcsException("Separate process fetch error", errors.get(0));
    }
    VcsException commandError = CommandLineUtil.getCommandLineError("git fetch", result);
    if (commandError != null) {
      if (isOutOfMemoryError(result)) {
        LOG.warn("There is not enough memory for git fetch, teamcity.git.fetch.process.max.memory=" + getFetchProcessMaxMemory() + ", try to increase it.");
        clean(repository);
      }
      throw commandError;
    }
    if (result.getStderr().length() > 0) {
      LOG.warn("Error output produced by git fetch");
      LOG.warn(result.getStderr());
    }
  }

  private boolean isOutOfMemoryError(ExecResult result) {
    return result.getStderr().contains("java.lang.OutOfMemoryError");
  }

  /**
   * Clean out garbage in case of errors
   * @param db repository
   */
  private void clean(Repository db) {
    //When jgit loads new pack into repository, it first writes it to file
    //incoming_xxx.pack. When it tries to open such pack we can run out of memory.
    //In this case incoming_xxx.pack files will waste disk space.
    //See TW-13450 for details
    File objectsDir = db.getObjectsDirectory();
    for (File f : objectsDir.listFiles()) {
      if (f.isFile() && f.getName().startsWith("incoming_") && f.getName().endsWith(".pack")) {
        FileUtil.delete(f);
      }
    }
  }

  /**
   * Get classpath for fetch process
   *
   * @return classpath for fetch process
   */
  private String getFetchClasspath() {
    return ClasspathUtil.composeClasspath(new Class[] {
      Fetcher.class,
      VcsRoot.class,
      ProgressMonitor.class,
      VcsPersonalSupport.class,
      Logger.class,
      Settings.class,
      com.jcraft.jsch.JSch.class,
      Decoder.class,
      gnu.trove.TObjectHashingStrategy.class,
      BranchSupport.class,
      EncryptUtil.class
    }, null, null);
  }

  /**
   * Get path to java executable for fetch process, "${java.home}/bin/java" by default
   *
   * @return path to java executable
   */
  private String getFetchProcessJavaPath() {
    final String jdkHome = System.getProperty("java.home");
    File defaultJavaExec = new File(jdkHome.replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "java");
    return TeamCityProperties.getProperty("teamcity.git.fetch.process.java.exec", defaultJavaExec.getAbsolutePath());
  }

  /**
   * Get maximum amount of memory for fetch process, 512M by default
   *
   * @return maximum amount of memory for fetch process
   */
  private String getFetchProcessMaxMemory() {
    return TeamCityProperties.getProperty("teamcity.git.fetch.process.max.memory", "512M");
  }

  /**
   * Check if fetch should be run in separate process, true by default
   *
   * @return true if fetch should be run in separate process
   */
  private boolean separateProcessForFetch() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.fetch.separate.process");
  }

  public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
    OperationContext context = createContext(vcsRoot, "connection test");
    Settings s = context.getSettings();
    File repositoryTempDir = null;
    try {
      repositoryTempDir = FileUtil.createTempDirectory("git-testcon", "");
      s.setUserDefinedRepositoryPath(repositoryTempDir);
      Repository r = context.getRepository();
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Opening connection for " + s.debugInfo());
        }
        final Transport tn = openTransport(s.getAuthSettings(), r, s.getRepositoryFetchURL());
        try {
          final FetchConnection c = tn.openFetch();
          try {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Checking references... " + s.debugInfo());
            }
            String refName = GitUtils.branchRef(s.getBranch());
            boolean refFound = false;
            for (final Ref ref : c.getRefs()) {
              if (refName.equals(ref.getName())) {
                LOG.info("The branch reference found " + refName + "=" + ref.getObjectId() + " for " + s.debugInfo());
                refFound = true;
                break;
              }
            }
            if (!refFound) {
              throw new VcsException("The branch " + refName + " was not found in the repository " + s.getRepositoryFetchURL().toString());
            }
          } finally {
            c.close();
          }
        } finally {
          tn.close();
        }
        if (!s.getRepositoryFetchURL().equals(s.getRepositoryPushURL())) {
          final Transport push = openTransport(s.getAuthSettings(), r, s.getRepositoryPushURL());
          try {
            final PushConnection c = push.openPush();
            try {
              c.getRefs();
            } finally {
              c.close();
            }
          } finally {
            tn.close();
          }
        }
        return null;
      } catch (NotSupportedException nse) {
        throw friendlyNotSupportedException(vcsRoot, s, nse);
      } catch (TransportException te) {
        throw friendlyTransportException(te);
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      if (repositoryTempDir != null) FileUtil.delete(repositoryTempDir);
      context.close();
    }
  }

  @Override
  public TestConnectionSupport getTestConnectionSupport() {
    return this;
  }

  public OperationContext createContext(VcsRoot root, String operation) {
    return new OperationContext(this, root, operation);
  }

  ServerPaths getServerPaths() {
    return myServerPaths;
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
    synchronized (getRepositoryLock(s.getRepositoryDir())) {
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
        final Transport tn = openTransport(s.getAuthSettings(), r, s.getRepositoryPushURL());
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
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
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

  /**
   * Open transport for the repository
   *
   * @param authSettings authentication settings
   * @param r   the repository to open
   * @param url the URL to open
   * @return the transport instance
   * @throws NotSupportedException if transport is not supported
   * @throws VcsException          if there is a problem with configuring the transport
   */
  public Transport openTransport(Settings.AuthSettings authSettings, Repository r, final URIish url) throws NotSupportedException, VcsException {
    final URIish authUrl = authSettings.createAuthURI(url);
    checkUrl(url);
    final Transport t = Transport.open(r, authUrl);
    t.setCredentialsProvider(authSettings.toCredentialsProvider());
    if (t instanceof SshTransport) {
      SshTransport ssh = (SshTransport)t;
      ssh.setSshSessionFactory(getSshSessionFactory(authSettings, url));
    }
    t.setTimeout(getCloneTimeout());
    return t;
  }

  /**
   * This is a work-around for an issue http://youtrack.jetbrains.net/issue/TW-9933.
   * Due to bug in jgit (https://bugs.eclipse.org/bugs/show_bug.cgi?id=315564),
   * in the case of not-existing local repository we get an obscure exception:
   * 'org.eclipse.jgit.errors.NotSupportedException: URI not supported: x:/git/myrepo.git',
   * while URI is correct.
   *
   * It often happens when people try to access a repository located on a mapped network
   * drive from the TeamCity started as Windows service.
   *
   * If repository is local and is not exists this method throws a friendly exception.
   *
   * @param url URL to check
   * @throws VcsException if url points to not-existing local repository
   */
  private void checkUrl(final URIish url) throws VcsException {
    if (!url.isRemote()) {
      File localRepository = new File(url.getPath());
      if (!localRepository.exists()) {
        String error = "Cannot access repository " + url.toString();
        if (SystemInfo.isWindows) {
          error += ". If TeamCity is run as a Windows service, it cannot access network mapped drives. Make sure this is not your case.";
        }
        throw new VcsException(error);
      }
    }
  }

  private int getFetchTimeout() {
    return TeamCityProperties.getInteger("teamcity.git.fetch.timeout", 18000);
  }

  private int getCloneTimeout() {
    return TeamCityProperties.getInteger("teamcity.git.clone.timeout", 18000);
  }

  /**
   * Get appropriate session factory object using settings
   *
   * @param authSettings a vcs root settings
   * @return session factory object
   * @throws VcsException in case of problems with creating object
   */
  private SshSessionFactory getSshSessionFactory(Settings.AuthSettings authSettings, URIish url) throws VcsException {
    switch (authSettings.getAuthMethod()) {
      case PRIVATE_KEY_DEFAULT:
        return authSettings.isIgnoreKnownHosts() ? mySshSessionFactoryKnownHostsIgnored : mySshSessionFactory;
      case PRIVATE_KEY_FILE:
        try {
          return new PrivateKeyFileSshSessionFactory(authSettings);
        } catch (VcsAuthenticationException e) {
          //add url to exception
          throw new VcsAuthenticationException(url.toString(), e.getMessage().toString());
        }
      case PASSWORD:
        return PasswordSshSessionFactory.INSTANCE;
      default:
        throw new VcsAuthenticationException(url.toString(), "The authentication method " + authSettings.getAuthMethod() + " is not supported for SSH");
    }
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
    Collection<Ref> remotes = getRemoteRefs(root);
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
  Collection<Ref> getRemoteRefs(@NotNull final VcsRoot root) throws VcsException {
    OperationContext context = createContext(root, "list remote branches");
    Settings s = context.getSettings();
    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("git-ls-remote", "");
      s.setUserDefinedRepositoryPath(tmpDir);
      Repository db = context.getRepository();
      Transport transport = null;
      FetchConnection connection = null;
      try {
        transport = openTransport(s.getAuthSettings(), db, s.getRepositoryFetchURL());
        connection = transport.openFetch();
        return connection.getRefs();
      } catch (NotSupportedException nse) {
        throw friendlyNotSupportedException(root, s, nse);
      } catch (TransportException te) {
        throw friendlyTransportException(te);
      } finally {
        if (connection != null) connection.close();
        if (transport != null) transport.close();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      if (tmpDir != null) FileUtil.delete(tmpDir);
      context.close();
    }
  }

  public File getCachesDir() {
    return myCacheDir;
  }

  private Exception friendlyTransportException(TransportException te) {
    if (GitServerUtil.isUnknownHostKeyError(te)) {
      String originalMessage = te.getMessage();
      String message = originalMessage + ". Add this host to a known hosts database or check option 'Ignore Known Hosts Database'.";
      return new VcsException(message, te);
    } else {
      return te;
    }
  }

  private NotSupportedException friendlyNotSupportedException(VcsRoot root, Settings s, NotSupportedException nse)  {
    URIish fetchURI = s.getRepositoryFetchURL();
    if (GitServerUtil.isRedundantColon(fetchURI)) {
      //url with username looks like ssh://username/hostname:/path/to/repo - it will
      //confuse user even further, so show url without user name
      return new NotSupportedException(MessageFormat.format(JGitText.get().URINotSupported, root.getProperty(Constants.FETCH_URL)) +
                                      ". Make sure you don't have a colon after the host name.");
    } else {
      return nse;
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
}
