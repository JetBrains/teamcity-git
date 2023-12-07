/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleResolverImpl;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy.getPolicyWithErrorsIgnored;
import static jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIteratorFactory.create;

/**
 * This RevWalk class accepts checkout rules and traverses commits graph until it finds a commit matched by these rules.
 */
public class CheckoutRulesRevWalk extends LimitingRevWalk {
  private final CheckoutRules myCheckoutRules;
  private final Set<String> myCollectedUninterestingRevisions = new HashSet<>();
  private final Set<String> myReachedStopRevisions = new LinkedHashSet<>();
  private final Set<String> myStopRevisions = new HashSet<>();
  private final Set<String> myVisitedRevisions = new HashSet<>();
  private SubmoduleResolverImpl mySubmoduleResolver;
  private String myClosestPartiallyAffectedMergeCommit = null;
  private Set<ObjectId> myStopRevisionsParents = new HashSet<>();

  CheckoutRulesRevWalk(@NotNull final ServerPluginConfig config,
                       @NotNull final OperationContext context,
                       @NotNull final CheckoutRules checkoutRules) throws VcsException {
    super(config, context);
    sort(RevSort.TOPO);
    myCheckoutRules = checkoutRules;
  }

  public void setStopRevisions(@NotNull Collection<String> stopRevisions) {
    myStopRevisions.clear();
    myStopRevisions.addAll(stopRevisions);
    myStopRevisionsParents.clear();
  }

  @Nullable
  public RevCommit getNextMatchedCommit() throws IOException {
    rememberStopRevisionsParents();
    markStopRevisionsParentsAsUninteresting(this);

    while (next() != null) {
      RevCommit cc = getCurrentCommit();
      if (myVisitedRevisions.isEmpty()) {
        // initialize the submodules resolver for the first revision only
        initSubmodulesResolver();
      }

      if (myCollectedUninterestingRevisions.contains(cc.name())) continue;
      handleStopRevision(cc);
      myVisitedRevisions.add(cc.name());
      if (isCurrentCommitIncluded()) {
        return cc;
      }
    }

    return null;
  }

  private void rememberStopRevisionsParents() throws IOException {
    ObjectDatabase objectDatabase = getRepository().getObjectDatabase();
    for (String stopRev: myStopRevisions) {
      ObjectId stopRevId = ObjectId.fromString(GitUtils.versionRevision(stopRev));
      if (!objectDatabase.has(stopRevId)) continue;

      RevCommit stopCommit = parseCommit(stopRevId);
      for (RevCommit p: stopCommit.getParents()) {
        myStopRevisionsParents.add(p.getId());
      }
    }

  }

  private void handleStopRevision(@NotNull RevCommit cc) {
    if (myStopRevisions.contains(cc.name())) {
      myReachedStopRevisions.add(cc.name());
    }
  }

  private void markStopRevisionsParentsAsUninteresting(@NotNull RevWalk revWalk) throws IOException {
    for (ObjectId parentId: myStopRevisionsParents) {
      revWalk.markUninteresting(revWalk.parseCommit(parentId));
    }
  }

  @NotNull
  public Set<String> getVisitedRevisions() {
    return Collections.unmodifiableSet(myVisitedRevisions);
  }

  @NotNull
  public List<String> getReachedStopRevisions() {
    return new ArrayList<>(myReachedStopRevisions);
  }

  @Nullable
  public String getClosestPartiallyAffectedMergeCommit() {
    return myClosestPartiallyAffectedMergeCommit;
  }

  private boolean isCurrentCommitIncluded() throws IOException {
    checkCurrentCommit();

    final RevCommit[] parents = getCurrentCommit().getParents();

    final GitVcsRoot gitRoot = getGitRoot();
    if (parents.length > 1) {
      // merge commit is interesting only if it changes interesting files when comparing to both of its parents,
      // otherwise, if files are changed comparing to one parent only, then we need to go deeper through the commit graph
      // and find the actual commit which changed the files
      int numAffectedParents = 0;

      Set<RevCommit> uninterestingParents = new HashSet<>();
      for (RevCommit parent : parents) {
        if (isAffectedByCheckoutRules(gitRoot, parent)) {
          numAffectedParents++;
        } else {
          for (RevCommit p : parents) {
            if (p != parent) {
              uninterestingParents.add(p);
            }
          }
        }
      }

      Set<String> uninterestingCommits = new HashSet<>();
      if (uninterestingParents.size() < parents.length) {
        uninterestingCommits = collectUninterestingCommits(uninterestingParents);
      } else { // uninterestingParents.size() == parents.length
        // we have a merge commit which does not change anything interesting in the files tree comparing to all of its parents
        // this can happen in two cases:
        // 1) interesting files were not changed by this commit
        // 2) interesting files were changed in all parents of this commit in the same way (mutual merges)
        // in either case we should go deeper, but since the files state is the same for all parents
        // we can treat the commits reachable from one of the parent as uninteresting

        // we want to mark as many commits as possible as uniteresting
        // for this we'll collect reachable commits each time excluding one parent only
        // then we'll choose the biggest collection
        for (RevCommit p : parents) {
          Set<RevCommit> uninterestingExcludingOneParent = new HashSet<>(uninterestingParents);
          uninterestingExcludingOneParent.remove(p);
          Set<String> res = collectUninterestingCommits(uninterestingExcludingOneParent);
          if (res.size() > uninterestingCommits.size()) {
            uninterestingCommits = res;
          }
        }
      }

      myCollectedUninterestingRevisions.addAll(uninterestingCommits);

      if (numAffectedParents == 1 && myClosestPartiallyAffectedMergeCommit == null) {
        // it is possible that stop revisions will prevent us from finding the actual commit which changed the interesting files
        // in this case we'll use the first met partially affected merge commit as a result
        myClosestPartiallyAffectedMergeCommit = getCurrentCommit().name();
      }

      return numAffectedParents > 1;
    }

    return isAffectedByCheckoutRules(gitRoot, parents.length > 0 ? parents[0] : null);
  }

  private void initSubmodulesResolver() {
    mySubmoduleResolver = createSubmoduleResolver(getCurrentCommit());
  }

  @NotNull
  private SubmoduleResolverImpl createSubmoduleResolver(@NotNull RevCommit commit) {
    return new SubmoduleResolverImpl(getContext(), getContext().getCommitLoader(), getRepository(), commit, "");
  }

  private boolean isAffectedByCheckoutRules(@NotNull GitVcsRoot gitVcsRoot, @Nullable RevCommit parent) throws IOException {
    try (VcsChangeTreeWalk tw = newVcsChangeTreeWalk()) {
      tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(gitVcsRoot));
      tw.setRecursive(true);
      addTree(gitVcsRoot, tw, getRepository(), getCurrentCommit());
      if (parent != null) {
        addTree(gitVcsRoot, tw, getRepository(), parent);
      }

      while (tw.next()) {
        final String path = tw.getPathString();
        if (path.equals(SubmoduleResolverImpl.GITMODULES_FILE_NAME) &&
            gitVcsRoot.isCheckoutSubmodules() &&
            mySubmoduleResolver != null &&
            !mySubmoduleResolver.getSubmoduleResolverConfigCommit().equals(getCurrentCommit().name())) {

          // our submodules resolver is no longer relevant, so it's safer to reset it and
          // from now one create a new one for each new commit
          mySubmoduleResolver = null;
        }

        if (myCheckoutRules.shouldInclude(path)) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  private Set<String> collectUninterestingCommits(@NotNull final Set<RevCommit> uninterestingParents) throws IOException {
    Set<String> result = new HashSet<>();

    RevCommit[] parents = getCurrentCommit().getParents();

    Set<RevCommit> interestingParents = new HashSet<>();
    for (RevCommit c: parents) {
      if (uninterestingParents.contains(c)) continue;
      interestingParents.add(c);
    }

    // we should mark all commits reachable from uninteresting parents as uninteresting, except those which are also reachable from the interesting parents
    RevWalk walk = newRevWalk();

    // for performance reasons it's important to avoid going beyond stop revisions
    markStopRevisionsParentsAsUninteresting(walk);

    try {
      for (RevCommit p: interestingParents) {
        walk.markUninteresting(walk.parseCommit(p.getId()));
      }

      for (RevCommit p: uninterestingParents) {
        walk.markStart(walk.parseCommit(p.getId()));
      }

      RevCommit next;
      while ((next = walk.next()) != null) {
        handleStopRevision(next);
        result.add(next.name());
      }
    } finally {
      walk.reset();
      walk.close();
      walk.dispose();
    }

    return result;
  }

  @NotNull
  private RevWalk newRevWalk() {
    return new RevWalk(getObjectReader());
  }

  @NotNull
  private VcsChangeTreeWalk newVcsChangeTreeWalk() {
    return new VcsChangeTreeWalk(getObjectReader(), getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog());
  }

  private void addTree(@NotNull GitVcsRoot root,
                       @NotNull TreeWalk tw,
                       @NotNull Repository db,
                       @NotNull RevCommit commit) throws IOException {
    if (root.isCheckoutSubmodules()) {
      SubmoduleResolverImpl submoduleResolver = mySubmoduleResolver != null ? mySubmoduleResolver : createSubmoduleResolver(commit);
      SubmodulesCheckoutPolicy checkoutPolicy = getPolicyWithErrorsIgnored(root.getSubmodulesCheckoutPolicy(), true);
      tw.addTree(create(db, commit, submoduleResolver, root.getRepositoryFetchURL().toString(), "", checkoutPolicy, false, myCheckoutRules));
    } else {
      tw.addTree(commit.getTree().getId());
    }
  }
}
