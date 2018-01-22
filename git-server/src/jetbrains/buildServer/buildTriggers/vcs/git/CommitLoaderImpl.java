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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;

public class CommitLoaderImpl implements CommitLoader {

  private static final Logger LOG = Logger.getInstance(CommitLoaderImpl.class.getName());
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(CommitLoaderImpl.class.getName() + ".Performance");

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
      fetch(db, root.getRepositoryFetchURL(), asList(spec), new FetchSettings(root.getAuthSettings()));
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
    synchronized (myRepositoryManager.getWriteLock(repositoryDir)) {
      final long finish = System.currentTimeMillis();
      Map<String, Ref> oldRefs = new HashMap<String, Ref>(db.getAllRefs());
      PERFORMANCE_LOG.debug("[waitForWriteLock] repository: " + repositoryDir.getAbsolutePath() + ", took " + (finish - start) + "ms");
      myFetchCommand.fetch(db, fetchURI, refspecs, settings);
      Map<String, Ref> newRefs = new HashMap<String, Ref>(db.getAllRefs());
      myMapFullPath.invalidateRevisionsCache(db, oldRefs, newRefs);
    }
  }

  @NotNull
  public RevCommit getCommit(@NotNull Repository repository, @NotNull ObjectId commitId) throws IOException {
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
    fetch(repository, root.getRepositoryFetchURL(), asList(spec), new FetchSettings(root.getAuthSettings()));
  }
}
