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
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleException;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class GitCollectChangesPolicy implements CollectChangesBetweenRepositories, RevisionMatchedByCheckoutRulesCalculator {

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

  @Nullable
  public String getLatestRevisionAcceptedByCheckoutRules(@NotNull VcsRoot root,
                                                         @NotNull CheckoutRules rules,
                                                         @NotNull String startRevision,
                                                         @NotNull Collection<String> stopRevisions) throws VcsException {
    return getLatestRevisionAcceptedByCheckoutRules(root, rules, startRevision, stopRevisions, null);
  }

  @Nullable
  public String getLatestRevisionAcceptedByCheckoutRules(@NotNull VcsRoot root,
                                                         @NotNull CheckoutRules rules,
                                                         @NotNull String startRevision,
                                                         @NotNull Collection<String> stopRevisions,
                                                         @Nullable Set<String> visited) throws VcsException {
    Disposable name = NamedDaemonThreadFactory.patchThreadName("Computing the latest commit affected by checkout rules: " + rules +
                                                               " in VCS root: " + LogUtil.describe(root) + ", start revision: " + startRevision);
    try {
      OperationContext context = myVcs.createContext(root, "latest revision affecting checkout", createProgress());
      GitVcsRoot gitRoot = context.getGitRoot();
      return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
        try {
          Repository r = context.getRepository();
          ModificationDataRevWalk revWalk = new ModificationDataRevWalk(myConfig, context);

          revWalk.markStart(getCommits(r, revWalk, Collections.singleton(startRevision)));
          for (RevCommit c: getCommits(r, revWalk, stopRevisions)) {
            for (RevCommit p: c.getParents()) {
              revWalk.markUninteresting(p);
            }
          }

          Set<String> uninteresting = new HashSet<>();
          while (revWalk.next() != null) {
            RevCommit commit = revWalk.getCurrentCommit();
            if (uninteresting.remove(commit.name())) { // no need to keep all uninteresting commits in memory as RevWalk only visits every commit once
              continue;
            }
            if (visited != null) {
              visited.add(commit.name());
            }

            if (revWalk.isIncludedByCheckoutRules(rules, uninteresting)) {
              return commit.name();
            }
          }

          return null;
        } catch (Exception e) {
          throw context.wrapException(e);
        } finally {
          context.close();
        }
      });
    } finally {
      name.dispose();
    }
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


  @NotNull
  private List<RevCommit> getCommits(@NotNull RepositoryStateData state, @NotNull Repository r, @NotNull RevWalk walk) throws IOException {
    final Collection<String> revisions = state.getBranchRevisions().values();

    return getCommits(r, walk, revisions);
  }

  @NotNull
  private List<RevCommit> getCommits(final @NotNull Repository r, final @NotNull RevWalk walk, @NotNull final Collection<String> revisions)
    throws IOException {
    List<RevCommit> result = new ArrayList<>();
    for (String revision : revisions) {
      ObjectId id = ObjectId.fromString(GitUtils.versionRevision(revision));
      if (r.getObjectDatabase().has(id)) {
        RevObject obj = walk.parseAny(id);
        if (obj.getType() == Constants.OBJ_COMMIT)
          result.add((RevCommit) obj);
      }
    }
    return result;
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
    @NotNull private final Set<String> myRemoteRefs;

    @NotNull private final Collection<CommitLoader.RefCommit> myRevisions = new ArrayList<>();

    public FetchContext(@NotNull final OperationContext context) throws VcsException {
      myContext = context;
      myFetchSettings = new FetchSettings(context.getGitRoot().getAuthSettings(), context.getProgress());
      myRemoteRefs = myVcs.getRemoteRefs(context.getRoot()).keySet().stream().filter(r -> r.startsWith("refs/")).collect(Collectors.toSet());
    }

    @NotNull
    FetchContext withRevisions(@NotNull Map<String, String> revisions, boolean tips) {
      myRevisions.addAll(expandRefs(revisions, tips));
      return this;
    }

    @NotNull
    FetchContext withFromRevisions(@NotNull Map<String, String> revisions) {
      return withRevisions(revisions, false);
    }

    @NotNull
    FetchContext withToRevisions(@NotNull Map<String, String> revisions) {
      return withRevisions(revisions, true);
    }

    @NotNull
    private Collection<CommitLoader.RefCommit> expandRefs(@NotNull Map<String, String> revisions, boolean tips) {
      return revisions.entrySet().stream().filter(e -> !isEmpty(e.getKey())).map(e ->  new CommitLoader.RefCommit() {
        @NotNull
        @Override
        public String getRef() {
          return GitUtils.expandRef(e.getKey());
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
      }).collect(Collectors.toList());
    }

    void fetchIfNoCommitsOrFail() throws VcsException, IOException {
      myCommitLoader.loadCommits(myContext, myContext.getGitRoot().getRepositoryFetchURL().get(), myRevisions, myRemoteRefs, myFetchSettings);
    }
  }
}
