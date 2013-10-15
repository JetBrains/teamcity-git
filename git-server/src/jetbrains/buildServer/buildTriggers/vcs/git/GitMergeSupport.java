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
import java.util.*;

public class GitMergeSupport implements MergeSupport, GitServerExtension {

  private final GitVcsSupport myVcs;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;

  public GitMergeSupport(@NotNull GitVcsSupport vcs,
                         @NotNull RepositoryManager repositoryManager,
                         @NotNull TransportFactory transportFactory) {
    myVcs = vcs;
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
        result = doMerge(gitRoot, db, srcRevision, dstBranch, message);
        if (result.isMergePerformed() && result.isSuccess())
          return result;
        attemptsLeft--;
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
  private MergeResult doMerge(@NotNull GitVcsRoot gitRoot,
                              @NotNull Repository db,
                              @NotNull String srcRevision,
                              @NotNull String dstBranch,
                              @NotNull String message) throws IOException, VcsException {
    RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(dstBranch)).setDestination(GitUtils.expandRef(dstBranch)).setForceUpdate(true);
    myVcs.fetch(db, gitRoot.getRepositoryFetchURL(), spec, gitRoot.getAuthSettings());

    Ref dstRef = db.getRef(dstBranch);
    ObjectId dstBranchLastCommit = dstRef.getObjectId();

    ResolveMerger merger = (ResolveMerger) MergeStrategy.RESOLVE.newMerger(db, true);
    boolean mergeSuccessful = merger.merge(dstBranchLastCommit, ObjectId.fromString(srcRevision));
    if (!mergeSuccessful) {
      List<String> conflicts = merger.getUnmergedPaths();
      Collections.sort(conflicts);
      return MergeResult.createMergeError(conflicts);
    }

    ObjectInserter inserter = db.newObjectInserter();
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder dcb = dc.builder();

    dcb.addTree(new byte[]{}, 0, db.getObjectDatabase().newReader(), merger.getResultTreeId());
    inserter.flush();
    dcb.finish();

    RevWalk revWalk = new RevWalk(db);
    RevCommit commit = revWalk.parseCommit(ObjectId.fromString(srcRevision));

    ObjectId writtenTreeId = dc.writeTree(inserter);

    CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setCommitter(new PersonIdent("teamcity", "teamcity@buildserver", new Date(), TimeZone.getDefault()));
    commitBuilder.setAuthor(commit.getAuthorIdent());
    commitBuilder.setMessage(message);
    commitBuilder.addParentId(dstBranchLastCommit);
    commitBuilder.addParentId(ObjectId.fromString(srcRevision));
    commitBuilder.setTreeId(writtenTreeId);

    ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();

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
}
