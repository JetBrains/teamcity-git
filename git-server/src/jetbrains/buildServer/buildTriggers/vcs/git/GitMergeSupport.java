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

import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class GitMergeSupport implements MergeSupport, GitServerExtension {

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;

  public GitMergeSupport(@NotNull GitVcsSupport vcs,
                         @NotNull CommitLoader commitLoader,
                         @NotNull RepositoryManager repositoryManager,
                         @NotNull TransportFactory transportFactory) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myTransportFactory = transportFactory;
    myVcs.addExtension(this);
  }

  @NotNull
  public MergeResult merge(@NotNull VcsRoot root,
                           @NotNull String srcRevision,
                           @NotNull String dstBranch,
                           @NotNull String message,
                           @NotNull MergeOptions options) throws VcsException {
    OperationContext context = myVcs.createContext(root, "merge");
    try {
      GitVcsRoot gitRoot = context.getGitRoot();
      Repository db = context.getRepository();
      int attemptsLeft = 3;
      MergeResult result = MergeResult.createMergeSuccessResult();
      while (attemptsLeft > 0) {
        try {
          result = doMerge(context, gitRoot, db, srcRevision, dstBranch, message);
          if (result.isMergePerformed() && result.isSuccess())
            return result;
          attemptsLeft--;
        } catch (IOException e) {
          return MergeResult.createMergeError(e.getMessage());
        } catch (VcsException e) {
          return MergeResult.createMergeError(e.getMessage());
        }
      }
      return result;
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  @NotNull
  public Map<MergeTask, MergeResult> tryMerge(@NotNull VcsRoot root,
                                              @NotNull List<MergeTask> tasks,
                                              @NotNull MergeOptions options) throws VcsException {
    Map<MergeTask, MergeResult> mergeResults = new HashMap<MergeTask, MergeResult>();
    OperationContext context = myVcs.createContext(root, "merge");
    try {
      Repository db = context.getRepository();
      for (MergeTask t : tasks) {
        ObjectId src = ObjectId.fromString(t.getSourceRevision());
        ObjectId dst = ObjectId.fromString(t.getDestinationRevision());
        ResolveMerger merger = (ResolveMerger) MergeStrategy.RESOLVE.newMerger(db, true);
        try {
          boolean success = merger.merge(dst, src);
          if (success) {
            mergeResults.put(t, MergeResult.createMergeSuccessResult());
          } else {
            mergeResults.put(t, MergeResult.createMergeError(merger.getUnmergedPaths()));
          }
        } catch (IOException mergeException) {
          mergeResults.put(t, MergeResult.createMergeError(mergeException.getMessage()));
        }
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
    return mergeResults;
  }

  @NotNull
  private MergeResult doMerge(@NotNull OperationContext context,
                              @NotNull GitVcsRoot gitRoot,
                              @NotNull Repository db,
                              @NotNull String srcRevision,
                              @NotNull String dstBranch,
                              @NotNull String message) throws IOException, VcsException {
    RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(dstBranch)).setDestination(GitUtils.expandRef(dstBranch)).setForceUpdate(true);
    myCommitLoader.fetch(db, gitRoot.getRepositoryFetchURL(), asList(spec), gitRoot.getAuthSettings());
    RevCommit srcCommit = myCommitLoader.findCommit(db, srcRevision);
    if (srcCommit == null)
      srcCommit = myCommitLoader.loadCommit(context, gitRoot, srcRevision);

    Ref dstRef = db.getRef(dstBranch);
    RevCommit dstBranchLastCommit = myCommitLoader.loadCommit(context, gitRoot, dstRef.getObjectId().name());
    ObjectId commitId;
    try {
      commitId = mergeCommits(gitRoot, db, srcCommit, dstBranchLastCommit, message);
    } catch (MergeFailedException e) {
      return MergeResult.createMergeError(e.getConflicts());
    }

    synchronized (myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir())) {
      final Transport tn = myTransportFactory.createTransport(db, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings());
      try {
        final PushConnection c = tn.openPush();
        try {
          RemoteRefUpdate ru = new RemoteRefUpdate(db, null, commitId, GitUtils.expandRef(dstBranch), false, null, dstBranchLastCommit);
          c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(GitUtils.expandRef(dstBranch), ru));
          switch (ru.getStatus()) {
            case UP_TO_DATE:
            case OK:
              return MergeResult.createMergeSuccessResult();
            default:
              return MergeResult.createMergeError("Push failed, " + ru.getMessage());
          }
        } finally {
          c.close();
        }
      } finally {
        tn.close();
      }
    }
  }


  private ObjectId mergeCommits(@NotNull GitVcsRoot gitRoot,
                                @NotNull Repository db,
                                @NotNull RevCommit srcCommit,
                                @NotNull RevCommit dstCommit,
                                @NotNull String message) throws IOException, MergeFailedException {
    RevWalk walk = new RevWalk(db);
    try {
      if (walk.isMergedInto(walk.parseCommit(dstCommit), walk.parseCommit(srcCommit)))
        return srcCommit;
    } finally {
      walk.release();
    }

    ResolveMerger merger = (ResolveMerger) MergeStrategy.RESOLVE.newMerger(db, true);
    boolean mergeSuccessful = merger.merge(dstCommit, srcCommit);
    if (!mergeSuccessful) {
      List<String> conflicts = merger.getUnmergedPaths();
      Collections.sort(conflicts);
      throw new MergeFailedException(conflicts);
    }

    ObjectInserter inserter = db.newObjectInserter();
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder dcb = dc.builder();

    dcb.addTree(new byte[]{}, 0, db.getObjectDatabase().newReader(), merger.getResultTreeId());
    inserter.flush();
    dcb.finish();

    ObjectId writtenTreeId = dc.writeTree(inserter);

    CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setCommitter(gitRoot.getTagger(db));
    commitBuilder.setAuthor(gitRoot.getTagger(db));
    commitBuilder.setMessage(message);
    commitBuilder.addParentId(dstCommit);
    commitBuilder.addParentId(srcCommit);
    commitBuilder.setTreeId(writtenTreeId);

    ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();
    return commitId;
  }


  private static class MergeFailedException extends Exception {
    private List<String> myConflicts;

    private MergeFailedException(@NotNull List<String> conflicts) {
      myConflicts = conflicts;
    }

    @NotNull
    public List<String> getConflicts() {
      return myConflicts;
    }
  }
}
