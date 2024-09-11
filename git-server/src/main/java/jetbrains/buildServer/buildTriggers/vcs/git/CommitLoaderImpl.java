

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.RevisionNotFoundException;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsOperationRejectedException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class CommitLoaderImpl implements CommitLoader {

  static final Logger LOG = Logger.getInstance(CommitLoaderImpl.class.getName());
  public static final Logger PERFORMANCE_LOG = Logger.getInstance(CommitLoaderImpl.class.getName() + ".Performance");

  private final RepositoryManager myRepositoryManager;
  private final GitRepoOperations myGitRepoOperations;
  private final GitMapFullPath myMapFullPath;
  private final ServerPluginConfig myPluginConfig;
  private final FetchSettingsFactory myFetchSettingsFactory;

  public CommitLoaderImpl(@NotNull RepositoryManager repositoryManager,
                          @NotNull GitRepoOperations gitRepoOperations,
                          @NotNull GitMapFullPath mapFullPath,
                          @NotNull ServerPluginConfig pluginConfig,
                          @NotNull FetchSettingsFactory fetchSettingsFactory) {
    myRepositoryManager = repositoryManager;
    myGitRepoOperations = gitRepoOperations;
    myMapFullPath = mapFullPath;
    myPluginConfig = pluginConfig;
    myFetchSettingsFactory = fetchSettingsFactory;
    myMapFullPath.setCommitLoader(this);
  }

  @NotNull
  public RevCommit loadCommit(@NotNull OperationContext context,
                              @NotNull GitVcsRoot root,
                              @NotNull String revision) throws VcsException, IOException {
    String commitSHA = GitUtils.versionRevision(revision);
    Repository db = context.getRepository(root);
    ObjectId commitId = ObjectId.fromString(commitSHA);
    try {
      return getCommit(db, commitId);
    } catch (IOException ex) {
      //ignore error, will try to fetch
    }

    LOG.debug("Cannot find commit " + commitSHA + " in repository " + root.debugInfo() + ", fetch branch " + root.getRef());
    fetchBranchData(root, db);

    try {
      return getCommit(db, commitId);
    } catch (IOException e) {
      LOG.debug("Cannot find commit " + commitSHA + " in the branch " + root.getRef() +
                " of repository " + root.debugInfo() + ", fetch all branches");
      RefSpec spec = new RefSpec().setSourceDestination("refs/*", "refs/*").setForceUpdate(true);
      fetch(db, root.getRepositoryFetchURL().get(), new FetchSettings(root.getAuthSettings(), asList(spec)));
      try {
        return getCommit(db, commitId);
      } catch (IOException e1) {
        throw new RevisionNotFoundException("Cannot find commit " + commitSHA + " in repository " + root.debugInfo());
      }
    }
  }

  public void fetch(@NotNull Repository db,
                    @NotNull URIish fetchURI,
                    @NotNull FetchSettings settings) throws IOException, VcsException {
    File repositoryDir = db.getDirectory();
    assert repositoryDir != null : "Non-local repository";
    final long start = System.currentTimeMillis();

    ReentrantLock lock = myRepositoryManager.getWriteLock(repositoryDir);
    lock.lock();
    try {
      final long finish = System.currentTimeMillis();
      final long waitTime = finish - start;
      if (waitTime > 20000) {
        // if wait time was significant, report it in progress
        settings.getProgress().reportProgress("Waited for exclusive lock in cloned directory, wait time: " + waitTime + "ms");
      }
      PERFORMANCE_LOG.debug("[waitForWriteLock] repository: " + repositoryDir.getAbsolutePath() + ", took " + waitTime + "ms");
      doFetch(db, fetchURI, settings);
    } finally {
      lock.unlock();
    }
  }

  private void doFetch(@NotNull final Repository db,
                       @NotNull final URIish fetchURI,
                       @NotNull final FetchSettings settings) throws IOException, VcsException {
    if (settings.getRefSpecs().isEmpty()) return;

    Map<String, Ref> oldRefs = new HashMap<>(db.getAllRefs());
    myGitRepoOperations.fetchCommand(fetchURI.toString()).fetch(db, fetchURI, settings);
    if (myPluginConfig.refreshObjectDatabaseAfterFetch()) {
      db.getObjectDatabase().refresh();
    }
    Map<String, Ref> newRefs = new HashMap<>(db.getAllRefs());
    myMapFullPath.invalidateRevisionsCache(db, oldRefs, newRefs);
  }


  @NotNull
  private ReentrantLock acquireWriteLock(@NotNull File repo, long timeout) throws VcsException {
    final ReentrantLock lock = myRepositoryManager.getWriteLock(repo);
    if (timeout > 0) {
      try {
        if (lock.tryLock(timeout, TimeUnit.SECONDS)) {
          return lock;
        }
        throw new VcsOperationRejectedException("Write lock timeout: failed to acquire lock in " + timeout + StringUtil.pluralize(" second") + " for the Git clone directory: " + repo.getAbsolutePath());
      } catch (InterruptedException e) {
        throw new VcsException("Commit loader operation interrupted", e);
      }
    }
    lock.lock();
    return lock;
  }

  @NotNull
  private static Collection<RefCommit> findRefsToFetch(@NotNull OperationContext context,
                                                       @NotNull Repository db,
                                                       @NotNull Collection<RefCommit> revisions,
                                                       boolean checkTipRefs,
                                                       boolean throwErrors) throws VcsException {
    final Set<RefCommit> refsToFetch = new HashSet<>();

    Map<String, Ref> currentRefTips = Collections.emptyMap();
    if (checkTipRefs) {
      currentRefTips = loadCurrentRefTips(db, revisions);
    }

    try (RevWalk walk = new RevWalk(db)) {
      for (RefCommit r : revisions) {
        final String ref = GitUtils.expandRef(r.getRef());
        final String revNumber = GitUtils.versionRevision(r.getCommit());
        try {
          if (checkTipRefs && r.isRefTip() && !GitServerUtil.isTag(ref)) {
            // For the refs from the new ("to") state we check if these refs in the local clone point to the same revisions
            // this is only done prior to determining if we need to fetch these refs selectively (hence checkTipRefs argument)
            String localRev = null;
            Ref localRef = currentRefTips.get(ref);
            if (localRef != null) {
              ObjectId localRevId = localRef.getObjectId();
              if (localRevId != null) {
                localRev = localRevId.getName();
              }
            }
            if (localRev == null || !localRev.equals(revNumber))
              refsToFetch.add(r);
          } else {
            // In all other cases we just check if revisions exist in the local clone
            // This is done for all refs from the old ("from") state and also for all refs from the "to" state
            // after selective fetch was done - as a last resort we will attempt to fetch all refs (not selectively)
            // to obtain revisions that are still missing
            walk.parseCommit(ObjectId.fromString(revNumber));
          }
        } catch (IncorrectObjectTypeException e) {
          LOG.warn("Ref " + ref + " points to a non-commit " + revNumber + " for " + context.getGitRoot().debugInfo());
        } catch (MissingObjectException e) {
          refsToFetch.add(r);
        } catch (Exception e) {
          LOG.warnAndDebugDetails("Unexpected exception while trying to parse commit " + revNumber,  e);
          refsToFetch.add(r);
        }
      }

      if (refsToFetch.isEmpty()) {
        return refsToFetch;
      }


      LOG.debug("Revisions missing in the local repository: " +
                refsToFetch.stream().map(e -> e.getRef() + ": " + e.getCommit()).collect(Collectors.joining(", ")) + " for " +
                context.getGitRoot().debugInfo());

      if (throwErrors) {
        final Set<String> missingTips =
          refsToFetch.stream().filter(RefCommit::isRefTip).map(e -> e.getRef() + ": " + e.getCommit()).collect(Collectors.toSet());

        if (missingTips.size() > 0) {
          final VcsException error = new VcsException("Revisions missing in the local repository: " + StringUtil.join(missingTips, ", "));
          error.setRecoverable(context.getPluginConfig().treatMissingBranchTipAsRecoverableError());
          throw error;
        }
      }
      return refsToFetch;
    }
  }

  private static @NotNull Map<String, Ref> loadCurrentRefTips(@NotNull Repository db, @NotNull Collection<RefCommit> revisions) {
    Map<String, Ref> allRefsMap = new HashMap<>();
    try {
      List<String> refTips = new ArrayList<>();
      for (RefCommit r: revisions) {
        final String ref = GitUtils.expandRef(r.getRef());
        if (r.isRefTip() && !GitServerUtil.isTag(ref)) {
          refTips.add(ref);
        }
      }

      if (!refTips.isEmpty()) {
        for (Ref ref: db.getRefDatabase().getRefsByPrefix(refTips.toArray(new String[0]))) {
          allRefsMap.put(ref.getName(), ref);
        }
      }
    } catch (IOException e) {
      LOG.warnAndDebugDetails("Unexpected exception while trying to load refs from the local clone: " + db.getDirectory().getAbsolutePath(), e);
    }
    return allRefsMap;
  }

  public void loadCommits(@NotNull OperationContext context,
                          @NotNull URIish fetchURI,
                          @NotNull Collection<RefCommit> revisions,
                          @NotNull Set<String> remoteRefs) throws IOException, VcsException {
    if (revisions.isEmpty()) return;

    final Repository db = context.getRepository();
    final File repositoryDir = db.getDirectory();
    assert repositoryDir != null : "Non-local repository";

    Collection<RefCommit> refsToFetch = findRefsToFetch(context, db, revisions, true, false);
    if (refsToFetch.isEmpty()) return;

    final long start = System.currentTimeMillis();
    final ReentrantLock lock = acquireWriteLock(repositoryDir, context.getPluginConfig().repositoryWriteLockTimeout());
    try {
      final long finish = System.currentTimeMillis();
      final long waitTime = finish - start;
      if (waitTime > 20000) {
        // if wait time was significant, report it in progress
        context.getProgress().reportProgress("Waited for exclusive lock in cloned directory, wait time: " + waitTime + "ms");
      }
      PERFORMANCE_LOG.debug("[waitForWriteLock] repository: " + repositoryDir.getAbsolutePath() + ", took " + waitTime + "ms");

      refsToFetch = findRefsToFetch(context, db, refsToFetch, true, false);
      if (refsToFetch.isEmpty()) return;

      final boolean includeTags = context.getGitRoot().isReportTags();
      FetchSettings settings = myFetchSettingsFactory.getFetchSettings(context, refsToFetch, revisions, remoteRefs, includeTags);
      doFetch(db, fetchURI, settings);

      refsToFetch = findRefsToFetch(context, db, refsToFetch, false, false);
      if (refsToFetch.isEmpty()) return;

      final boolean fetchAllRefsDisabled = !context.getPluginConfig().fetchAllRefsEnabled();
      if (fetchAllRefsDisabled && refsToFetch.stream().noneMatch(RefCommit::isRefTip)) return;

      settings = myFetchSettingsFactory.getFetchSettings(context, includeTags || !fetchAllRefsDisabled);
      doFetch(db, fetchURI, settings);
      findRefsToFetch(context, db, refsToFetch, false, true);
    } finally {
      lock.unlock();
    }
  }

  @NotNull
  public RevCommit getCommit(@NotNull Repository repository, @NotNull ObjectId commitId) throws IOException {
    final long start = System.currentTimeMillis();
    RevWalk walk = new RevWalk(repository);
    try {
      return walk.parseCommit(commitId);
    } finally {
      walk.close();
      final long finish = System.currentTimeMillis();
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug(
          "[RevWalk.parseCommit] repository=" + repository.getDirectory().getAbsolutePath() + ", commit=" + commitId.name() + ", took: " +
          (finish - start) + "ms");
      }
    }
  }

  @Nullable
  public RevCommit findCommit(@NotNull Repository r, @NotNull String sha) {
    try {
      return getCommit(r, ObjectId.fromString(sha));
    } catch (Exception e) {
      return null;
    }
  }

  private void fetchBranchData(@NotNull GitVcsRoot root, @NotNull Repository repository)
    throws VcsException, IOException {
    final String refName = GitUtils.expandRef(root.getRef());
    RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
    fetch(repository, root.getRepositoryFetchURL().get(), new FetchSettings(root.getAuthSettings(), asList(spec)));
  }
}