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
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleException;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class GitCollectChangesPolicy implements CollectChangesBetweenRepositories {

  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());

  private final GitVcsSupport myVcs;
  private final VcsOperationProgressProvider myProgressProvider;
  private final CommitLoader myCommitLoader;
  private final ServerPluginConfig myConfig;
  private final RepositoryManager myRepositoryManager;

  public GitCollectChangesPolicy(@NotNull GitVcsSupport vcs,
                                 @NotNull VcsOperationProgressProvider progressProvider,
                                 @NotNull CommitLoader commitLoader,
                                 @NotNull ServerPluginConfig config,
                                 @NotNull RepositoryManager repositoryManager) {
    myVcs = vcs;
    myProgressProvider = progressProvider;
    myCommitLoader = commitLoader;
    myConfig = config;
    myRepositoryManager = repositoryManager;
  }


  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot fromRoot,
                                               @NotNull RepositoryStateData fromState,
                                               @NotNull VcsRoot toRoot,
                                               @NotNull RepositoryStateData toState,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    return collectChanges(toRoot, fromState, toState, checkoutRules);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull RepositoryStateData fromState,
                                               @NotNull RepositoryStateData toState,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    OperationContext context = myVcs.createContext(root, "collecting changes", createProgress());
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      List<ModificationData> changes = new ArrayList<ModificationData>();
      try {
        Repository r = context.getRepository();
        ModificationDataRevWalk revWalk = new ModificationDataRevWalk(myConfig, context);
        revWalk.sort(RevSort.TOPO);
        ensureRepositoryStateLoadedFor(context, r, true, toState, fromState);
        markStart(r, revWalk, toState);
        markUninteresting(r, revWalk, fromState, toState);
        while (revWalk.next() != null) {
          changes.add(revWalk.createModificationData());
        }
      } catch (Exception e) {
        if (e instanceof SubmoduleException) {
          SubmoduleException se = (SubmoduleException) e;
          Set<String> affectedBranches = getBranchesWithCommit(context.getRepository(), toState, se.getMainRepositoryCommit());
          throw context.wrapException(se.addBranches(affectedBranches));
        }
        throw context.wrapException(e);
      } finally {
        context.close();
      }
      return changes;
    });
  }


  @NotNull
  private Set<String> getBranchesWithCommit(@NotNull Repository r, @NotNull RepositoryStateData state, @NotNull String commit) {
    return Collections.emptySet();
  }


  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot root) throws VcsException {
    return myVcs.getCurrentState(root);
  }

  public void ensureRepositoryStateLoadedFor(@NotNull final OperationContext context,
                                             @NotNull final Repository repo,
                                             final boolean failOnFirstError,
                                             @NotNull final RepositoryStateData... states) throws Exception {
    boolean isFirst = failOnFirstError;
    if (myConfig.usePerBranchFetch()) {
      for (RepositoryStateData state : states) {
        ensureRepositoryStateLoadedOneFetchPerBranch(context, state, isFirst);
        isFirst = false;
      }
    } else {
      FetchAllRefs fetch = new FetchAllRefs(context.getProgress(), repo, context.getGitRoot(), states);
      for (RepositoryStateData state : states) {
        ensureRepositoryStateLoaded(context, repo, state, fetch, isFirst);
        isFirst = false;
      }
    }
  }

  @NotNull
  public RepositoryStateData fetchAllRefs(@NotNull final OperationContext context,
                                          @NotNull final GitVcsRoot root) throws VcsException {
    try {
      final RepositoryStateData currentState = myVcs.getCurrentState(root);
      new FetchAllRefs(context.getProgress(), context.getRepository(), context.getGitRoot(), currentState).fetchTrackedRefs();
      return currentState;
    } catch (Exception e) {
      throw new VcsException(e.getMessage(), e);
    }
  }

  private void ensureRepositoryStateLoaded(@NotNull OperationContext context,
                                           @NotNull Repository db,
                                           @NotNull RepositoryStateData state,
                                           @NotNull FetchAllRefs fetch,
                                           boolean throwErrors) throws Exception {
    GitVcsRoot root = context.getGitRoot();
    for (Map.Entry<String, String> entry : state.getBranchRevisions().entrySet()) {
      String ref = entry.getKey();
      String revision = GitUtils.versionRevision(entry.getValue());
      if (myCommitLoader.findCommit(db, revision) != null)
        continue;

      if (!fetch.isInvoked())
        fetch.fetchTrackedRefs();

      if (myCommitLoader.findCommit(db, revision) != null)
        continue;

      if (!fetch.allRefsFetched())
        fetch.fetchAllRefs();

      try {
        myCommitLoader.getCommit(db, ObjectId.fromString(revision));
      } catch (IncorrectObjectTypeException e) {
        LOG.warn("Ref " + ref + " points to a non-commit " + revision);
      } catch (Exception e) {
        if (throwErrors) {
          VcsException error = new VcsException("Cannot find revision " + revision + " in branch " + ref + " in VCS root " + LogUtil.describe(root), e);
          error.setRecoverable(myConfig.treatMissingBranchTipAsRecoverableError());
          throw error;
        } else {
          LOG.warn("Cannot find revision " + revision + " in branch " + ref + " in VCS root " + LogUtil.describe(root));
        }
      }
    }
  }

  private void ensureRepositoryStateLoadedOneFetchPerBranch(@NotNull OperationContext context, @NotNull RepositoryStateData state, boolean throwErrors) throws Exception {
    GitVcsRoot root = context.getGitRoot();
    for (Map.Entry<String, String> entry : state.getBranchRevisions().entrySet()) {
      String branch = entry.getKey();
      String revision = entry.getValue();
      GitVcsRoot branchRoot = root.getRootForBranch(branch);
      try {
        myCommitLoader.loadCommit(context, branchRoot, GitUtils.versionRevision(revision));
      } catch (Exception e) {
        if (throwErrors) {
          VcsException error = new VcsException("Cannot find revision " + revision + " in branch " + branch + " in VCS root " + LogUtil.describe(root), e);
          error.setRecoverable(myConfig.treatMissingBranchTipAsRecoverableError());
          throw error;
        } else {
          LOG.warn("Cannot find revision " + revision + " in branch " + branch + " of root " + LogUtil.describe(context.getGitRoot()));
        }
      }
    }
  }

  private void markUninteresting(@NotNull Repository r,
                                 @NotNull ModificationDataRevWalk walk,
                                 @NotNull final RepositoryStateData fromState,
                                 @NotNull final RepositoryStateData toState) throws IOException {
    List<RevCommit> commits = getCommits(fromState, r, walk);
    if (commits.isEmpty()) {//if non of fromState revisions found - limit commits by toState
      commits = getCommits(toState, r, walk);
      LOG.info("Cannot find commits referenced by fromState, will not report any changes");
    }
    for (RevCommit commit : commits) {
      walk.markUninteresting(commit);
    }
  }


  private void markStart(@NotNull Repository r, @NotNull RevWalk walk, @NotNull RepositoryStateData state) throws IOException {
    walk.markStart(getCommits(state, r, walk));
  }


  private List<RevCommit> getCommits(@NotNull RepositoryStateData state, @NotNull Repository r, @NotNull RevWalk walk) throws IOException {
    List<RevCommit> revisions = new ArrayList<RevCommit>();
    for (String revision : state.getBranchRevisions().values()) {
      ObjectId id = ObjectId.fromString(GitUtils.versionRevision(revision));
      if (r.hasObject(id)) {
        RevObject obj = walk.parseAny(id);
        if (obj.getType() == Constants.OBJ_COMMIT)
          revisions.add((RevCommit) obj);
      }
    }
    return revisions;
  }


  @NotNull
  private GitProgress createProgress() {
    try {
      return new GitVcsOperationProgress(myProgressProvider.getProgress());
    } catch (IllegalStateException e) {
      return GitProgress.NO_OP;
    }
  }

  private class FetchAllRefs {
    private final GitProgress myProgress;
    private final Repository myDb;
    private final GitVcsRoot myRoot;
    private final Set<String> myAllRefNames;
    private boolean myInvoked = false;
    private boolean myAllRefsFetched = false;

    private FetchAllRefs(@NotNull GitProgress progress,
                         @NotNull Repository db,
                         @NotNull GitVcsRoot root,
                         @NotNull RepositoryStateData... states) {
      myProgress = progress;
      myDb = db;
      myRoot = root;
      myAllRefNames = getAllRefNames(states);
    }

    void fetchTrackedRefs() throws IOException, VcsException {
      myInvoked = true;
      FetchSettings settings = new FetchSettings(myRoot.getAuthSettings(), myProgress);
      myCommitLoader.fetch(myDb, myRoot.getRepositoryFetchURL(), calculateRefSpecsForFetch(), settings);
    }

    void fetchAllRefs() throws IOException, VcsException {
      myInvoked = true;
      myAllRefsFetched = true;
      FetchSettings settings = new FetchSettings(myRoot.getAuthSettings(), myProgress);
      myCommitLoader.fetch(myDb, myRoot.getRepositoryFetchURL(), Collections.singleton(new RefSpec("refs/*:refs/*").setForceUpdate(true)), settings);
    }

    boolean isInvoked() {
      return myInvoked;
    }

    boolean allRefsFetched() {
      return myAllRefsFetched;
    }

    private Collection<RefSpec> calculateRefSpecsForFetch() throws VcsException {
      List<RefSpec> specs = new ArrayList<RefSpec>();
      Map<String, Ref> remoteRepositoryRefs;
      try {
        remoteRepositoryRefs = myVcs.getRemoteRefs(myRoot.getOriginalRoot());
      } catch (Exception e) {
        //when failed to get state of the remote repository try to collect changes in all refs we have
        remoteRepositoryRefs = null;
      }
      for (String ref : myAllRefNames) {
        if (remoteRepositoryRefs != null && remoteRepositoryRefs.containsKey(ref))
          specs.add(new RefSpec(ref + ":" + ref).setForceUpdate(true));
      }
      return specs;
    }

    private Set<String> getAllRefNames(@NotNull RepositoryStateData... states) {
      Set<String> refs = new HashSet<String>();
      for (RepositoryStateData state : states) {
        for (String ref : state.getBranchRevisions().keySet()) {
          if (!isEmpty(ref))
            refs.add(GitUtils.expandRef(ref));
        }
      }
      return refs;
    }
  }
}
