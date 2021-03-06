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

  private static final Logger LOG = Logger.getInstance(CommitLoaderImpl.class.getName());
  public static final Logger PERFORMANCE_LOG = Logger.getInstance(CommitLoaderImpl.class.getName() + ".Performance");

  private final RepositoryManager myRepositoryManager;
  private final FetchCommand myFetchCommand;
  private final GitMapFullPath myMapFullPath;
  private final ServerPluginConfig myPluginConfig;

  public CommitLoaderImpl(@NotNull RepositoryManager repositoryManager,
                          @NotNull FetchCommand fetchCommand,
                          @NotNull GitMapFullPath mapFullPath, ServerPluginConfig pluginConfig) {
    myRepositoryManager = repositoryManager;
    myFetchCommand = fetchCommand;
    myMapFullPath = mapFullPath;
    myPluginConfig = pluginConfig;
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
      fetch(db, root.getRepositoryFetchURL().get(), asList(spec), new FetchSettings(root.getAuthSettings()));
      try {
        return getCommit(db, commitId);
      } catch (IOException e1) {
        throw new RevisionNotFoundException("Cannot find commit " + commitSHA + " in repository " + root.debugInfo());
      }
    }
  }

  public void fetch(@NotNull Repository db,
                    @NotNull URIish fetchURI,
                    @NotNull Collection<RefSpec> refspecs,
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
      doFetch(db, fetchURI, refspecs, settings);
    } finally {
      lock.unlock();
    }
  }

  private void doFetch(@NotNull final Repository db,
                       @NotNull final URIish fetchURI,
                       @NotNull final Collection<RefSpec> refspecs,
                       @NotNull final FetchSettings settings) throws IOException, VcsException {
    if (refspecs.isEmpty()) return;

    Map<String, Ref> oldRefs = new HashMap<>(db.getAllRefs());
    myFetchCommand.fetch(db, fetchURI, refspecs, settings);
    if (myPluginConfig.refreshObjectDatabaseAfterFetch()) {
      db.getObjectDatabase().refresh();
    }
    Map<String, Ref> newRefs = new HashMap<>(db.getAllRefs());
    myMapFullPath.invalidateRevisionsCache(db, oldRefs, newRefs);
  }

  public void loadCommits(@NotNull OperationContext context,
                          @NotNull URIish fetchURI,
                          @NotNull Collection<RefCommit> revisions,
                          @NotNull Set<String> remoteRefs,
                          @NotNull FetchSettings settings) throws IOException, VcsException {
    if (revisions.isEmpty()) return;

    final Repository db = context.getRepository();
    final File repositoryDir = db.getDirectory();
    assert repositoryDir != null : "Non-local repository";

    Collection<RefCommit> missingRevisions = findLocallyMissingRevisions(context, db, revisions, false);
    if (missingRevisions.isEmpty()) return;

    final long start = System.currentTimeMillis();
    final ReentrantLock lock = acquireWriteLock(repositoryDir, context.getPluginConfig().repositoryWriteLockTimeout());
    try {
      final long finish = System.currentTimeMillis();
      final long waitTime = finish - start;
      if (waitTime > 20000) {
        // if wait time was significant, report it in progress
        settings.getProgress().reportProgress("Waited for exclusive lock in cloned directory, wait time: " + waitTime + "ms");
      }
      PERFORMANCE_LOG.debug("[waitForWriteLock] repository: " + repositoryDir.getAbsolutePath() + ", took " + waitTime + "ms");

      missingRevisions = findLocallyMissingRevisions(context, db, missingRevisions, false);
      if (missingRevisions.isEmpty()) return;

      final Set<String> filteredRemoteRefs = getFilteredRemoteRefs(context, remoteRefs); // unlike remoteRefs, which includes all remote refs, doesn't include tags if not enabled
      final boolean fetchRemoteRefs = shouldFetchRemoteRefs(context, revisions, filteredRemoteRefs);
      final Collection<RefSpec> refSpecs = fetchRemoteRefs ? getRefSpecForRemoteRefs(filteredRemoteRefs) : getRefSpecForCurrentState(context, missingRevisions, remoteRefs);
      doFetch(db, fetchURI, refSpecs, settings);

      missingRevisions = findLocallyMissingRevisions(context, db, missingRevisions, false);
      if (missingRevisions.isEmpty()) return;

      final boolean fetchAllRefsDisabled = !context.getPluginConfig().fetchAllRefsEnabled();
      if (fetchAllRefsDisabled && missingRevisions.stream().noneMatch(RefCommit::isRefTip)) return;

      if (fetchAllRefsDisabled && !fetchRemoteRefs) {
        doFetch(db, fetchURI, getRefSpecForRemoteRefs(filteredRemoteRefs), settings);
      } else if (!fetchAllRefsDisabled) {
        doFetch(db, fetchURI, getAllRefSpec(), settings);
      }
      findLocallyMissingRevisions(context, db, missingRevisions, true);
    } finally {
      lock.unlock();
    }
  }

  private boolean shouldFetchRemoteRefs(@NotNull OperationContext context, @NotNull Collection<RefCommit> revisions, @NotNull Set<String> filteredRemoteRefs) {
    final float factor = context.getPluginConfig().fetchRemoteBranchesFactor();
    if (factor == 0) return false;

    final int currentStateNum = revisions.stream().map(RefCommit::getRef).collect(Collectors.toSet()).size();
    if (currentStateNum == 1) return false;

    final int remoteNum = filteredRemoteRefs.size();
    return remoteNum < currentStateNum || (float)currentStateNum / remoteNum >= factor;
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
    } else {
      lock.lock();
    }
    return lock;
  }

  @NotNull
  private Collection<RefCommit> findLocallyMissingRevisions(@NotNull OperationContext context,
                                                            @NotNull Repository db,
                                                            @NotNull Collection<RefCommit> revisions,
                                                            boolean throwErrors) throws VcsException {
    final Set<RefCommit> locallyMissing = new HashSet<>();

    try (RevWalk walk = new RevWalk(db)) {
      for (RefCommit r : revisions) {
        final String ref = GitUtils.expandRef(r.getRef());
        final String rev = r.getCommit();
        final String revNumber = GitUtils.versionRevision(rev);
        try {
          walk.parseCommit(ObjectId.fromString(revNumber));
        } catch (IncorrectObjectTypeException e) {
          LOG.warn("Ref " + ref + " points to a non-commit " + revNumber + " for " + context.getGitRoot().debugInfo());
        } catch (MissingObjectException e) {
          locallyMissing.add(r);
        } catch (Exception e) {
          LOG.warnAndDebugDetails("Unexpected exception while trying to parse commit " + revNumber,  e);
          locallyMissing.add(r);
        }
      }

      if (locallyMissing.isEmpty()) {
        return locallyMissing;
      }


      LOG.debug("Revisions missing in the local repository: " +
                locallyMissing.stream().map(e -> e.getRef() + ": " + e.getCommit()).collect(Collectors.joining(", ")) + " for " +
                context.getGitRoot().debugInfo());

      if (throwErrors) {
        final Set<String> missingTips =
          locallyMissing.stream().filter(RefCommit::isRefTip).map(e -> e.getRef() + ": " + e.getCommit()).collect(Collectors.toSet());

        if (missingTips.size() > 0) {
          final VcsException error = new VcsException("Revisions missing in the local repository: " + StringUtil.join(missingTips, ", "));
          error.setRecoverable(context.getPluginConfig().treatMissingBranchTipAsRecoverableError());
          throw error;
        }
      }
      return locallyMissing;
    }
  }

  private static final String REF_MISSING_FORMAT = "Ref %s is no longer present in the remote repository";
  private static final String REFS_MISSING_FORMAT = "Refs %s are no longer present in the remote repository";

  @NotNull
  private Collection<RefSpec> getRefSpecForCurrentState(@NotNull OperationContext context, @NotNull Collection<RefCommit> revisions, @NotNull Collection<String> remoteRefs) throws VcsException {
    final Set<RefSpec> result = new HashSet<>();
    final Set<String> missingTips = new HashSet<>();

    for (RefCommit r : revisions) {
      final String ref = r.getRef();
      final boolean existsRemotely = remoteRefs.contains(ref);
      if (existsRemotely) {
        result.add(new RefSpec(ref + ":" + ref).setForceUpdate(true));
        continue;
      }
      if (r.isRefTip()) {
        missingTips.add(ref);
      } else {
        LOG.debug(String.format(REF_MISSING_FORMAT, ref) + " for " + context.getGitRoot().debugInfo());
      }
    }

    final int remotelyMissingRefsNum = missingTips.size();
    if (remotelyMissingRefsNum > 0) {
      final String message = remotelyMissingRefsNum == 1 ?
                             String.format(REF_MISSING_FORMAT, missingTips.iterator().next()) :
                             String.format(REFS_MISSING_FORMAT, StringUtil.join(", ", missingTips));

      final VcsException exception = new VcsException(message);
      exception.setRecoverable(context.getPluginConfig().treatMissingBranchTipAsRecoverableError());
      throw exception;
    }
    return result;
  }

  @NotNull
  private Collection<RefSpec> getRefSpecForRemoteRefs(@NotNull Collection<String> refs) {
    return refs.stream().map(r -> new RefSpec(r + ":" + r).setForceUpdate(true)).collect(Collectors.toList());
  }

  @NotNull
  private Set<String> getFilteredRemoteRefs(@NotNull OperationContext context, @NotNull Set<String> refs) throws VcsException {
    final GitVcsRoot gitRoot = context.getGitRoot();
    final boolean reportTags = gitRoot.isReportTags();
    if (reportTags) return refs;

    final String defaultBranch = GitUtils.expandRef(gitRoot.getRef());
    return refs.stream().filter(r -> !GitServerUtil.isTag(r) || defaultBranch.equals(r)).collect(Collectors.toSet());
  }

  @NotNull
  private Set<RefSpec> getAllRefSpec() {
    return Collections.singleton(new RefSpec("refs/*:refs/*").setForceUpdate(true));
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
    fetch(repository, root.getRepositoryFetchURL().get(), asList(spec), new FetchSettings(root.getAuthSettings()));
  }
}
