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

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleResolverImpl;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
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
  public static final String TEAMCITY_MAX_CHECKED_COMMITS_PROP = "teamcity.git.checkoutRulesRevWalk.maxCheckedCommits";
  private final CheckoutRules myCheckoutRules;
  private final Set<String> myStopRevisions = new HashSet<>();
  private final List<String> myReachedStopRevisions = new ArrayList<>();
  private String myStartRevision;
  private final Set<String> myVisitedRevisions = new HashSet<>();
  private SubmoduleResolverImpl mySubmoduleResolver;
  private String myClosestPartiallyAffectedMergeCommit = null;
  private final Set<ObjectId> myStopRevisionsParents = new HashSet<>();

  private final static Logger LOG = Logger.getInstance(CheckoutRulesRevWalk.class);

  CheckoutRulesRevWalk(@NotNull final ServerPluginConfig config,
                       @NotNull final OperationContext context,
                       @NotNull final CheckoutRules checkoutRules) throws VcsException {
    super(config, context);
    sort(RevSort.TOPO);
    myCheckoutRules = checkoutRules;
  }

  public void setStartRevision(@NotNull RevCommit c) {
    myStartRevision = c.name();
  }

  public void setStopRevisions(@NotNull Collection<String> stopRevisions) {
    myStopRevisions.clear();
    myStopRevisions.addAll(stopRevisions);
    myStopRevisionsParents.clear();
  }

  @Nullable
  public RevCommit findMatchedCommit() throws IOException {
    int maxNumberOfCheckedCommits = TeamCityProperties.getInteger(TEAMCITY_MAX_CHECKED_COMMITS_PROP, 10_000);

    markStart(parseCommit(ObjectId.fromString(GitUtils.versionRevision(myStartRevision))));
    rememberStopRevisionsParents();
    markStopRevisionsParentsAsUninteresting(this);

    while (next() != null) {
      RevCommit cc = getCurrentCommit();
      checkIfStopRevision(cc.name());

      if (myVisitedRevisions.isEmpty()) {
        // initialize the submodules resolver for the first revision only
        initSubmodulesResolver();
      }

      if (cc.has(RevFlag.UNINTERESTING)) {
        // if stop revisions are not configured then underlying generator will not ignore uninteresting commits and
        // will pass to us all of them
        continue;
      }

      myVisitedRevisions.add(cc.name());
      if (isCurrentCommitIncluded()) {
        return cc;
      }

      if (myVisitedRevisions.size() >= maxNumberOfCheckedCommits) {
        LOG.info("Reached the limit of " + maxNumberOfCheckedCommits + " checked commits for the start revision: " + myStartRevision +
                 " and stop revisions: " + myStopRevisions + " in repository: " + getGitRoot().toString() + ", giving up");
        return null;
      }
    }

    return null;
  }

  private void checkIfStopRevision(@NotNull String revision) {
    if (myStopRevisions.contains(revision)) {
      myReachedStopRevisions.add(revision);
    }
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

      if (numAffectedParents == 1 && myClosestPartiallyAffectedMergeCommit == null) {
        // it is possible that stop revisions will prevent us from finding the actual commit which changed the interesting files
        // in this case we'll use the first met partially affected merge commit as a result
        myClosestPartiallyAffectedMergeCommit = getCurrentCommit().name();
      }

      if (numAffectedParents == 0) {
        // we have a merge commit which does not change anything interesting in the files tree comparing to all of its parents
        // this can happen in two cases:
        // 1) interesting files were not changed by this commit
        // 2) interesting files were changed in all parents of this commit in the same way (mutual merges)
        // in either case we should go deeper, but since the files state is the same for all parents
        // we can mark all the commits reachable from the parents (excluding those reachable from other parents) as uninteresting

        for (RevCommit parent: parents) {
          Set<RevCommit> uninteresting = new HashSet<>(Arrays.asList(parents));
          uninteresting.remove(parent);
          markReachableCommitsAsUninteresting(uninteresting, Collections.singletonList(parent));
        }

      } else if (numAffectedParents < parents.length) {
        Set<RevCommit> excluding = new HashSet<>();
        for (RevCommit parent: parents) {
          if (!uninterestingParents.contains(parent)) {
            excluding.add(parent);
          }
        }
        markReachableCommitsAsUninteresting(uninterestingParents, excluding);
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

  private int markReachableCommitsAsUninteresting(@NotNull final Collection<RevCommit> startFrom, @NotNull Collection<RevCommit> excluding) throws IOException {
    int numMarked = 0;
    // we should mark all commits reachable from uninteresting parents as uninteresting, except those which are also reachable from the interesting parents
    RevWalk walk = newRevWalk();

    // for performance reasons it's important to avoid going beyond stop revisions
    markStopRevisionsParentsAsUninteresting(walk);

    try {
      for (RevCommit p: excluding) {
        walk.markUninteresting(walk.parseCommit(p.getId()));
      }

      for (RevCommit p: startFrom) {
        walk.markStart(walk.parseCommit(p.getId()));
      }

      RevCommit next;
      while ((next = walk.next()) != null) {
        // mark as uninteresting all reachable commits in the main rev walk
        parseCommit(next.getId()).add(RevFlag.UNINTERESTING);
        checkIfStopRevision(next.name());
        numMarked++;
      }
    } finally {
      walk.reset();
      walk.close();
      walk.dispose();
    }
    return numMarked;
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
