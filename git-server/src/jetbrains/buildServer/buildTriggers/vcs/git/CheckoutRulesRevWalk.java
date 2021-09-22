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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This RevWalk class accepts checkout rules and traverses commits graph until it finds a commit matched by these rules.
 */
public class CheckoutRulesRevWalk extends LimitingRevWalk {
  private final CheckoutRules myCheckoutRules;
  private final Set<String> myCollectedUninterestingRevisions = new HashSet<>();
  private final Set<String> myVisitedRevisions = new HashSet<>();

  CheckoutRulesRevWalk(@NotNull final ServerPluginConfig config,
                       @NotNull final OperationContext context,
                       @NotNull final CheckoutRules checkoutRules) throws VcsException {
    super(config, context);
    myCheckoutRules = checkoutRules;
  }

  @Nullable
  public RevCommit getNextMatchedCommit() throws IOException, VcsException {
    while (next() != null) {
      if (myCollectedUninterestingRevisions.contains(getCurrentCommit().name())) continue;
      myVisitedRevisions.add(getCurrentCommit().name());
      if (isCurrentCommitIncluded()) {
        return getCurrentCommit();
      }
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
        try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(getRepository(), getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog())) {
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
        collectUninterestingCommits(parents, uninterestingParents);
      }

      return numAffectedParents > 1;
    }

    try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(getRepository(), getGitRoot().debugInfo(), getConfig().verboseTreeWalkLog())) {
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

  private void collectUninterestingCommits(@NotNull final RevCommit[] parents,
                                           @NotNull final Set<RevCommit> uninterestingParents) throws IOException {
    if (uninterestingParents.size() == parents.length) {
      // all parents are uninteresting, it means we should traverse the parents till the merge base and add all found commits as uninteresting
      RevWalk walk = new RevWalk(getRepository());
      try {
        walk.setRevFilter(RevFilter.MERGE_BASE);
        Set<RevCommit> starts = new HashSet<>();
        for (RevCommit p: parents) {
          starts.add(walk.parseCommit(p.getId()));
        }

        for (RevCommit c: starts) {
          walk.markStart(c);
        }

        RevCommit mergeBase = walk.next();
        walk.reset();

        walk.setRevFilter(RevFilter.ALL);
        for (RevCommit c: starts) {
          walk.markStart(c);
        }
        if (mergeBase != null) {
          walk.markUninteresting(mergeBase);
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
    RevWalk walk = new RevWalk(getRepository());
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
}
