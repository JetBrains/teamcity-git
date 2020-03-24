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
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleException;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
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
        ensureRepositoryStateLoadedFor(context, fromState, toState);
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
                                             @NotNull final RepositoryStateData state,
                                             boolean throwErrors) throws Exception {
    new FetchContext(context)
      .withRevisions(state.getBranchRevisions(), throwErrors)
      .fetchIfNoCommitsOrFail();
  }

  public void ensureRepositoryStateLoadedFor(@NotNull final OperationContext context,
                                             @NotNull final RepositoryStateData fromState,
                                             @NotNull final RepositoryStateData toState) throws Exception {
    new FetchContext(context)
      .withToRevisions(toState.getBranchRevisions())
      .withFromRevisions(fromState.getBranchRevisions())
      .fetchIfNoCommitsOrFail();
  }

  @NotNull
  public RepositoryStateData fetchAllRefs(@NotNull final OperationContext context,
                                          @NotNull final GitVcsRoot root) throws VcsException {
    try {
      final RepositoryStateData currentState = myVcs.getCurrentState(root);
      new FetchContext(context).withFromRevisions(currentState.getBranchRevisions()).fetchIfNoCommitsOrFail();
      return currentState;
    } catch (Exception e) {
      throw new VcsException(e.getMessage(), e);
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

  private class FetchContext {
    @NotNull private final OperationContext myContext;
    @NotNull private final FetchSettings myFetchSettings;
    @NotNull private  final Set<String> myRemoteRefs;

    @NotNull private final Collection<CommitLoader.RefCommit> myRevisions = new ArrayList<>();

    public FetchContext(@NotNull final OperationContext context) throws VcsException {
      myContext = context;
      myFetchSettings = new FetchSettings(context.getGitRoot().getAuthSettings(), context.getProgress());
      myRemoteRefs = myVcs.getRemoteRefs(context.getRoot()).keySet();
    }

    @NotNull
    FetchContext withRevisions(@NotNull Map<String, String> revisions, boolean tips) throws VcsException {
      myRevisions.addAll(filterRemoteExistingAndExpand(revisions, tips));
      return this;
    }

    @NotNull
    FetchContext withFromRevisions(@NotNull Map<String, String> revisions) throws VcsException {
      return withRevisions(revisions, false);
    }

    @NotNull
    FetchContext withToRevisions(@NotNull Map<String, String> revisions) throws VcsException {
      return withRevisions(revisions, true);
    }

    @NotNull
    private Collection<CommitLoader.RefCommit> filterRemoteExistingAndExpand(@NotNull Map<String, String> revisions, boolean tips) throws VcsException {
      final Collection<CommitLoader.RefCommit> existing = new ArrayList<>();
      final Set<String> missing = new HashSet<>();

      for (Map.Entry<String, String> e : revisions.entrySet()) {
        final String ref = e.getKey();
        if (isEmpty(ref)) continue;

        final String expandedRef = GitUtils.expandRef(ref);
        if (myRemoteRefs.contains(expandedRef)) {
          existing.add(new CommitLoader.RefCommit() {
            @NotNull
            @Override
            public String getRef() {
              return expandedRef;
            }

            @NotNull
            @Override
            public String getCommit() {
              return e.getValue();
            }

            @Override
            public boolean isRefTip() {
              return tips;
            }
          });
        } else {
          missing.add(ref);
        }
      }

      final int remotelyMissingRefsNum = missing.size();
      if (remotelyMissingRefsNum > 0) {
        final String message = StringUtil.pluralize("Ref", remotelyMissingRefsNum) + " " +
                               String.join(", ", missing) +
                               (remotelyMissingRefsNum == 1 ? " is" : " are") +
                               " no longer present in the remote repository for VCS root " + LogUtil.describe(myContext.getGitRoot());

        if (tips) {
          final VcsException exception = new VcsException(message);
          exception.setRecoverable(myConfig.treatMissingBranchTipAsRecoverableError());
          throw exception;
        }
        LOG.debug(message);
      }
      return existing;
    }

    void fetchIfNoCommitsOrFail() throws VcsException, IOException {
      myCommitLoader.loadCommits(myContext, myContext.getGitRoot().getRepositoryFetchURL().get(), myRevisions, myRemoteRefs, myFetchSettings);
    }
  }
}
