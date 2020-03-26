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
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class CommitLoaderImpl implements CommitLoader {

  private static final Logger LOG = Logger.getInstance(CommitLoaderImpl.class.getName());
  public static final Logger PERFORMANCE_LOG = Logger.getInstance(CommitLoaderImpl.class.getName() + ".Performance");

  private final RepositoryManager myRepositoryManager;
  private final FetchCommand myFetchCommand;
  private final GitMapFullPath myMapFullPath;

  public CommitLoaderImpl(@NotNull RepositoryManager repositoryManager,
                          @NotNull FetchCommand fetchCommand,
                          @NotNull GitMapFullPath mapFullPath) {
    myRepositoryManager = repositoryManager;
    myFetchCommand = fetchCommand;
    myMapFullPath = mapFullPath;
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
        throw new VcsException("Cannot find commit " + commitSHA + " in repository " + root.debugInfo());
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
    Map<String, Ref> oldRefs = new HashMap<>(db.getAllRefs());
    myFetchCommand.fetch(db, fetchURI, refspecs, settings);
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

    try (RevWalk walk = new RevWalk(db)) {
      revisions = findLocallyMissingRevisions(context, walk, revisions, false);
      if (revisions.isEmpty()) return;

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

        revisions = findLocallyMissingRevisions(context, walk, revisions, false);
        if (revisions.isEmpty()) return;

        doFetch(db, fetchURI, getRefSpecsForRevisions(revisions), settings);

        revisions = findLocallyMissingRevisions(context, walk, revisions, false);
        if (revisions.isEmpty()) return;

        final boolean fetchAllRefsDisabled = !context.getPluginConfig().fetchAllRefsEnabled();
        if (revisions.stream().noneMatch(RefCommit::isRefTip) && fetchAllRefsDisabled) return;

        doFetch(db, fetchURI, fetchAllRefsDisabled ? getRefSpecsForRemoteBranches(remoteRefs) : getAllRefSpec(), settings);
        findLocallyMissingRevisions(context, walk, revisions, true);
      } finally {
        lock.unlock();
      }
    }
  }

  @NotNull
  private Collection<RefCommit> findLocallyMissingRevisions(@NotNull OperationContext context,
                                                            @NotNull RevWalk walk,
                                                            @NotNull Collection<RefCommit> revisions,
                                                            boolean throwErrors) throws VcsException {
    final Set<RefCommit> locallyMissingRevisions = new HashSet<>();

    for (RefCommit r : revisions) {
      final String ref = GitUtils.expandRef(r.getRef());
      final String rev = r.getCommit();
      final String revNumber = GitUtils.versionRevision(rev);
      try {
        walk.parseCommit(ObjectId.fromString(revNumber));
      } catch (IncorrectObjectTypeException e) {
        LOG.warn("Ref " + ref + " points to a non-commit " + revNumber + " for VCS Root " + LogUtil.describe(context.getRoot()));
      } catch (Exception e) {
        if (throwErrors && r.isRefTip()) {
          final VcsException error = new VcsException("Cannot find revision " + revNumber + " for ref " + ref + " in VCS root " + LogUtil.describe(context.getRoot()) + " mirror", e);
          error.setRecoverable(context.getPluginConfig().treatMissingBranchTipAsRecoverableError());
          throw error;
        } else {
          locallyMissingRevisions.add(r);
        }
      }
    }

    if (!locallyMissingRevisions.isEmpty()) {
      LOG.debug("Revisions missing in the local repository: " +
                locallyMissingRevisions.stream().map(e -> e.getRef() + ": " + e.getCommit()).collect(Collectors.joining(", ")));
    }

    return locallyMissingRevisions;
  }

  @NotNull
  private Collection<RefSpec> getRefSpecsForRevisions(@NotNull Collection<RefCommit> revisions) {
    return revisions.stream().map(r -> new RefSpec(r.getRef() + ":" + r.getRef()).setForceUpdate(true)).collect(Collectors.toSet());
  }

  @NotNull
  private Collection<RefSpec> getRefSpecsForRemoteBranches(@NotNull Collection<String> refs) {
    return refs.stream().filter(r -> r.startsWith("refs/")).map(r -> new RefSpec(r + ":" + r).setForceUpdate(true))
      .collect(Collectors.toList());
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
