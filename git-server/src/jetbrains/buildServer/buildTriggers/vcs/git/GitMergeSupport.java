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
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;

public class GitMergeSupport implements MergeSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitMergeSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;
  private final ServerPluginConfig myPluginConfig;

  public GitMergeSupport(@NotNull GitVcsSupport vcs,
                         @NotNull CommitLoader commitLoader,
                         @NotNull RepositoryManager repositoryManager,
                         @NotNull TransportFactory transportFactory,
                         @NotNull ServerPluginConfig pluginConfig) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myTransportFactory = transportFactory;
    myPluginConfig = pluginConfig;
    myVcs.addExtension(this);
  }

  @NotNull
  public MergeResult merge(@NotNull VcsRoot root,
                           @NotNull String srcRevision,
                           @NotNull String dstBranch,
                           @NotNull String message,
                           @NotNull MergeOptions options) throws VcsException {
    LOG.info("Merge in root " + root + ", revision " + srcRevision + ", destination " + dstBranch);
    OperationContext context = myVcs.createContext(root, "merge");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        Repository db = context.getRepository();
        int attemptsLeft = myPluginConfig.getMergeRetryAttempts();
        MergeResult result;
        do {
          try {
            result = doMerge(context, gitRoot, db, srcRevision, dstBranch, message, options);
            if (result.isMergePerformed() && result.isSuccess()) {
              LOG.info("Merge successfully finished in root " + root + ", revision " + srcRevision + ", destination " + dstBranch);
              return result;
            }
            attemptsLeft--;
            LOG.info("Merge was not successful, root " + root + ", revision " + srcRevision + ", destination " + dstBranch + ", attempts left " + attemptsLeft);
          } catch (IOException e) {
            LOG.info("Merge failed, root " + root + ", revision " + srcRevision + ", destination " + dstBranch, e);
            return MergeResult.createMergeError(e.getMessage());
          } catch (VcsException e) {
            LOG.info("Merge failed, root " + root + ", revision " + srcRevision + ", destination " + dstBranch, e);
            return MergeResult.createMergeError(e.getMessage());
          }
        } while (attemptsLeft > 0);
        return result;
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }


  @NotNull
  public Map<MergeTask, MergeResult> tryMerge(@NotNull VcsRoot root,
                                              @NotNull List<MergeTask> tasks,
                                              @NotNull MergeOptions options) throws VcsException {
    Map<MergeTask, MergeResult> mergeResults = new HashMap<MergeTask, MergeResult>();
    OperationContext context = myVcs.createContext(root, "merge");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        Repository db = context.getRepository();
        for (MergeTask t : tasks) {
          ObjectId src = ObjectId.fromString(t.getSourceRevision());
          ObjectId dst = ObjectId.fromString(t.getDestinationRevision());
          ResolveMerger merger = (ResolveMerger) MergeStrategy.RECURSIVE.newMerger(db, true);
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
    });
  }

  @NotNull
  private MergeResult doMerge(@NotNull OperationContext context,
                              @NotNull GitVcsRoot gitRoot,
                              @NotNull Repository db,
                              @NotNull String srcRevision,
                              @NotNull String dstBranch,
                              @NotNull String message,
                              @NotNull MergeOptions options) throws IOException, VcsException {
    RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(dstBranch)).setDestination(GitUtils.expandRef(dstBranch)).setForceUpdate(true);
    myCommitLoader.fetch(db, gitRoot.getRepositoryFetchURL(), asList(spec), new FetchSettings(gitRoot.getAuthSettings()));
    RevCommit srcCommit = myCommitLoader.findCommit(db, srcRevision);
    if (srcCommit == null)
      srcCommit = myCommitLoader.loadCommit(context, gitRoot, srcRevision);

    Ref dstRef = db.getRef(dstBranch);
    RevCommit dstBranchLastCommit = myCommitLoader.loadCommit(context, gitRoot, dstRef.getObjectId().name());
    ObjectId commitId;
    try {
      commitId = mergeCommits(gitRoot, db, srcCommit, dstBranchLastCommit, message, options);
    } catch (MergeFailedException e) {
      LOG.debug("Merge error, root " + gitRoot + ", revision " + srcRevision + ", destination " + dstBranch, e);
      return MergeResult.createMergeError(e.getConflicts());
    }

    synchronized (myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir())) {
      final Transport tn = myTransportFactory.createTransport(db, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings(),
                                                              myPluginConfig.getPushTimeoutSeconds());
      try {
        RemoteRefUpdate ru = new RemoteRefUpdate(db, null, commitId, GitUtils.expandRef(dstBranch), false, null, dstBranchLastCommit);
        tn.push(NullProgressMonitor.INSTANCE, Collections.singletonList(ru));
        switch (ru.getStatus()) {
          case UP_TO_DATE:
          case OK:
            return MergeResult.createMergeSuccessResult();
          default:
            return MergeResult.createMergeError("Push failed, " + ru.getMessage());
        }
      } catch (IOException e) {
        LOG.debug("Error while pushing a merge commit, root " + gitRoot + ", revision " + srcRevision + ", destination " + dstBranch, e);
        throw e;
      } finally {
        tn.close();
      }
    }
  }


  @NotNull
  private ObjectId mergeCommits(@NotNull GitVcsRoot gitRoot,
                                @NotNull Repository db,
                                @NotNull RevCommit srcCommit,
                                @NotNull RevCommit dstCommit,
                                @NotNull String message,
                                @NotNull MergeOptions options) throws IOException, MergeFailedException {
    if (!alwaysCreateMergeCommit(options)) {
      RevWalk walk = new RevWalk(db);
      try {
        if (walk.isMergedInto(walk.parseCommit(dstCommit), walk.parseCommit(srcCommit))) {
          LOG.debug("Commit " + srcCommit.name() + " already merged into " + dstCommit + ", skip the merge");
          return srcCommit;
        }
      } finally {
        walk.release();
      }
    }

    if (tryRebase(options)) {
      LOG.debug("Run rebase, root " + gitRoot + ", revision " + srcCommit.name() + ", destination " + dstCommit.name());
      try {
        return rebase(gitRoot, db, srcCommit, dstCommit);
      } catch (MergeFailedException e) {
        if (enforceLinearHistory(options)) {
          LOG.debug("Rebase failed, root " + gitRoot + ", revision " + srcCommit.name() + ", destination " + dstCommit.name(), e);
          throw e;
        }
      } catch (IOException e) {
        if (enforceLinearHistory(options)) {
          LOG.debug("Rebase failed, root " + gitRoot + ", revision " + srcCommit.name() + ", destination " + dstCommit.name(), e);
          throw e;
        }
      }
    }

    ResolveMerger merger = (ResolveMerger) MergeStrategy.RECURSIVE.newMerger(db, true);
    boolean mergeSuccessful = merger.merge(dstCommit, srcCommit);
    if (!mergeSuccessful) {
      List<String> conflicts = merger.getUnmergedPaths();
      Collections.sort(conflicts);
      LOG.debug("Merge failed with conflicts, root " + gitRoot + ", revision " + srcCommit.name() + ", destination " + dstCommit.name() +
                ", conflicts " + conflicts);
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


  @NotNull
  private ObjectId rebase(@NotNull GitVcsRoot gitRoot,
                          @NotNull Repository db,
                          @NotNull RevCommit srcCommit,
                          @NotNull RevCommit dstCommit) throws IOException, MergeFailedException {
    RevWalk walk = new RevWalk(db);
    try {
      RevCommit src = walk.parseCommit(srcCommit);
      RevCommit dst = walk.parseCommit(dstCommit);
      walk.markStart(src);
      walk.markStart(dst);
      walk.setRevFilter(RevFilter.MERGE_BASE);
      RevCommit base = walk.next();

      Map<ObjectId, RevCommit> tree2commit = new HashMap<ObjectId, RevCommit>();
      RevCommit c;

      if (base != null) {
        walk.reset();
        walk.setRevFilter(RevFilter.ALL);
        walk.markStart(dst);
        walk.markUninteresting(base);
        while ((c = walk.next()) != null) {
          tree2commit.put(c.getTree().getId(), c);
        }
      }

      walk.reset();
      walk.markStart(src);
      walk.markUninteresting(dst);
      walk.sort(RevSort.TOPO);
      walk.sort(RevSort.REVERSE);

      Map<RevCommit, RevCommit> orig2rebased = new HashMap<RevCommit, RevCommit>();
      List<RevCommit> toRebase = new ArrayList<RevCommit>();
      while ((c = walk.next()) != null) {
        ObjectId treeId = c.getTree().getId();
        RevCommit existing = tree2commit.get(treeId);
        if (existing != null) {
          orig2rebased.put(c, existing);
        } else {
          if (c.getParentCount() > 1) {
            throw new MergeFailedException(asList("Rebase of merge commits is not supported"));
          } else {
            toRebase.add(c);
          }
        }
      }

      orig2rebased.put(toRebase.get(0).getParent(0), dstCommit);
      ObjectInserter inserter = db.newObjectInserter();
      for (RevCommit commit : toRebase) {
        RevCommit p = commit.getParent(0);
        RevCommit b = orig2rebased.get(p);
        ObjectId rebased = rebaseCommit(gitRoot, db, inserter, commit, b);
        orig2rebased.put(commit, walk.parseCommit(rebased));
      }

      return orig2rebased.get(toRebase.get(toRebase.size() - 1));
    } finally {
      walk.release();
    }
  }


  @NotNull
  private ObjectId rebaseCommit(@NotNull GitVcsRoot gitRoot,
                                @NotNull Repository db,
                                @NotNull ObjectInserter inserter,
                                @NotNull RevCommit original,
                                @NotNull RevCommit base) throws IOException, MergeFailedException {
    final RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit))
      return original;

    ResolveMerger merger = (ResolveMerger) MergeStrategy.RECURSIVE.newMerger(db, true);
    merger.setBase(parentCommit);
    merger.merge(original, base);

    if (merger.getResultTreeId() == null)
      throw new MergeFailedException(merger.getUnmergedPaths());


    if (base.getTree().getId().equals(merger.getResultTreeId()))
      return base;

    final CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(merger.getResultTreeId());
    cb.setParentId(base);
    cb.setAuthor(GitServerUtil.getAuthorIdent(original));
    cb.setCommitter(gitRoot.getTagger(db));
    cb.setMessage(GitServerUtil.getFullMessage(original));
    final ObjectId objectId = inserter.insert(cb);
    inserter.flush();
    return objectId;
  }


  private boolean tryRebase(@NotNull MergeOptions options) {
    String value = options.getOption("git.merge.rebase");
    if (value == null)
      return false;
    return Boolean.valueOf(value);
  }


  private boolean enforceLinearHistory(@NotNull MergeOptions options) {
    String value = options.getOption("git.merge.enforceLinearHistory");
    if (value == null)
      return false;
    return Boolean.valueOf(value);
  }


  private boolean alwaysCreateMergeCommit(@NotNull MergeOptions options) {
    String value = options.getOption("teamcity.merge.policy");
    if (value == null)
      return true;
    return "alwaysCreateMergeCommit".equals(value);
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
