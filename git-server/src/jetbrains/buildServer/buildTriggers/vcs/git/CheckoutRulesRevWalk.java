/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * This RevWalk class accepts checkout rules and traverses commits graph until it finds a commit matched by these rules.
 */
public class CheckoutRulesRevWalk extends LimitingRevWalk {
  private final CheckoutRules myCheckoutRules;
  private final Set<String> myCollectedUninterestingRevisions = new HashSet<>();
  private final Set<String> myVisitedRevisions = new HashSet<>();
  private final Map<String, List<ObjectId>> myMergeBasesCache = new HashMap<>();
  private final ObjectReader myReader;

  CheckoutRulesRevWalk(@NotNull final ServerPluginConfig config,
                       @NotNull final OperationContext context,
                       @NotNull final CheckoutRules checkoutRules) throws VcsException {
    super(context.getRepository().getObjectDatabase().newCachedDatabase().newReader(), config, context);
    myCheckoutRules = checkoutRules;
    myReader = getObjectReader();
  }

  @Nullable
  public RevCommit getNextMatchedCommit() throws IOException, VcsException {
    while (next() != null) {
      if (myCollectedUninterestingRevisions.contains(getCurrentCommit().name())) continue;
      myVisitedRevisions.add(getCurrentCommit().name());
      if (isCurrentCommitIncluded()) {
        return getCurrentCommit();
      }

      myMergeBasesCache.remove(getCurrentCommit().name());
    }

    return null;
  }

  @NotNull
  public Set<String> getVisitedRevisions() {
    return Collections.unmodifiableSet(myVisitedRevisions);
  }

  private boolean isCurrentCommitIncluded()
    throws VcsException, IOException {
    checkCurrentCommit();

    final RevCommit[] parents = getCurrentCommit().getParents();

    if (getCurrentCommit().getParentCount() > 1) {
      // merge commit is interesting only if it changes interesting files when comparing to both of its parents,
      // otherwise, if files are changed comparing to one parent only, then we need to go deeper through the commit graph
      // and find the actual commit which changed the files
      int numAffectedParents = 0;

      Set<RevCommit> uninterestingParents = new HashSet<>();
      for (RevCommit parent: parents) {
        try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myReader, getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog())) {
          tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(getGitRoot()));
          tw.setRecursive(true);
          getContext().addTree(getGitRoot(), tw, getRepository(), getCurrentCommit(), true, false, myCheckoutRules);
          getContext().addTree(getGitRoot(), tw, getRepository(), parent, true, false, myCheckoutRules);

          if (isAcceptedByCheckoutRules(myCheckoutRules, tw)) {
            numAffectedParents++;
          } else {
            for (RevCommit p: parents) {
              if (p != parent) {
                uninterestingParents.add(p);
              }
            }
          }
        }
      }

      if (numAffectedParents <= 1) {
        if (uninterestingParents.size() == parents.length) {
          // we have a merge commit with some parents
          // this merge commit does not change anything interesting in the files tree comparing to all of its parents (we already checked it above)
          // but it is possible that all parents already had all the same changes in the interesting files or in other words
          // there were mutual merges which brought changes of each parent to another parent and vice versa
          // (see case from LatestAcceptedRevisionTest.merge_commit_tree_does_not_have_difference_with_parents())
          // to solve this problem, we'll find a merge base of these parents (the nearest common ancestor) and then we'll check if there were changes
          // in files affected by checkout rules between the current merge commit and this merge base,
          // if there are changes, then we'll return the current merge commit as the one accepted by checkout rules

          if (hasInterestingCommitsSinceMergeBase()) {
            return true;
          }
        }

        collectUninterestingCommits(uninterestingParents);
      }

      return numAffectedParents > 1;
    }

    try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myReader, getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog())) {
      tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(getGitRoot()));
      tw.setRecursive(true);
      getContext().addTree(getGitRoot(), tw, getRepository(), getCurrentCommit(), true, false, myCheckoutRules);
      if (parents.length > 0) {
        getContext().addTree(getGitRoot(), tw, getRepository(), parents[0], true, false, myCheckoutRules);
      }

      if (isAcceptedByCheckoutRules(myCheckoutRules, tw)) return true;
    }

    return false;
  }

  private boolean isAcceptedByCheckoutRules(final @NotNull CheckoutRules rules, @NotNull final VcsChangeTreeWalk tw) throws IOException {
    while (tw.next()) {
      final String path = tw.getPathString();
      if (rules.shouldInclude(path)) {
        return true;
      }
    }
    return false;
  }

  private void collectUninterestingCommits(@NotNull final Set<RevCommit> uninterestingParents) throws IOException {
    RevCommit[] parents = getCurrentCommit().getParents();
    if (uninterestingParents.size() == parents.length) {
      // all parents are uninteresting, and we already check that there were no changes in files matched by checkout rules since the merge base
      List<ObjectId> mergeBases = findCurrentCommitMergeBases();

      RevWalk walk = newRevWalk();
      try {
        Set<RevCommit> starts = new HashSet<>();
        for (RevCommit p: parents) {
          starts.add(walk.parseCommit(p.getId()));
        }

        for (RevCommit c: starts) {
          walk.markStart(c);
        }
        for (ObjectId mbRevision: mergeBases) {
          walk.markUninteresting(walk.parseCommit(mbRevision));
        }

        RevCommit next;
        while ((next = walk.next()) != null) {
          myCollectedUninterestingRevisions.add(next.name());
        }
      } finally {
        walk.reset();
        walk.close();
        walk.dispose();
      }

      return;
    }

    Set<RevCommit> interestingParents = new HashSet<>();
    for (RevCommit c: parents) {
      if (uninterestingParents.contains(c)) continue;
      interestingParents.add(c);
    }

    // we should mark all commits reachable from uninteresting parents as uninteresting, except those which are also reachable from the interesting parents
    RevWalk walk = newRevWalk();
    try {
      for (RevCommit p: interestingParents) {
        walk.markUninteresting(walk.parseCommit(p.getId()));
      }

      for (RevCommit p: uninterestingParents) {
        walk.markStart(walk.parseCommit(p.getId()));
      }

      RevCommit next;
      while ((next = walk.next()) != null) {
        myCollectedUninterestingRevisions.add(next.name());
      }
    } finally {
      walk.reset();
      walk.close();
      walk.dispose();
    }
  }

  @NotNull
  private List<ObjectId> findCurrentCommitMergeBases() throws IOException {
    List<ObjectId> cached = myMergeBasesCache.get(getCurrentCommit().name());
    if (cached != null) return cached;

    RevWalk walk = newRevWalk();

    List<ObjectId> mergeBases;
    try {
      walk.setRevFilter(RevFilter.MERGE_BASE);
      Set<RevCommit> starts = new HashSet<>();
      for (RevCommit p: getCurrentCommit().getParents()) {
        starts.add(walk.parseCommit(p.getId()));
      }

      for (RevCommit c: starts) {
        walk.markStart(c);
      }

      mergeBases = new ArrayList<>();
      RevCommit mergeBase = walk.next();
      while (mergeBase != null) {
        mergeBases.add(mergeBase.getId());
        mergeBase = walk.next();
      }
    } finally {
      walk.reset();
      walk.close();
      walk.dispose();
    }

    myMergeBasesCache.put(getCurrentCommit().name(), mergeBases);
    return mergeBases;
  }

  private boolean hasInterestingCommitsSinceMergeBase() throws IOException, VcsException {
    List<ObjectId> mergeBases = findCurrentCommitMergeBases();
    for (ObjectId mergeBaseId: mergeBases) {
      try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myReader, getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog())) {
        tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(getGitRoot()));
        tw.setRecursive(true);
        getContext().addTree(getGitRoot(), tw, getRepository(), getCurrentCommit(), true, false, myCheckoutRules);
        getContext().addTree(getGitRoot(), tw, getRepository(), parseCommit(mergeBaseId), true, false, myCheckoutRules);

        if (isAcceptedByCheckoutRules(myCheckoutRules, tw)) return true;
      }
    }

    return false;
  }

  @NotNull
  private RevWalk newRevWalk() {
    return new RevWalk(myReader);
  }

  @Override
  public void close() {
    super.close();
    myReader.close();
  }
}
