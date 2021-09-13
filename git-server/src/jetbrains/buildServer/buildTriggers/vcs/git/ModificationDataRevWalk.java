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
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.SubmoduleAwareTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
class ModificationDataRevWalk extends RevWalk {

  private static final Logger LOG = Logger.getInstance(ModificationDataRevWalk.class.getName());

  private final ServerPluginConfig myConfig;
  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final Repository myRepository;
  private final int mySearchDepth;
  private int myNextCallCount = 0;
  private RevCommit myCurrentCommit;
  private int myNumberOfCommitsToVisit = -1;


  ModificationDataRevWalk(@NotNull ServerPluginConfig config, @NotNull OperationContext context) throws VcsException {
    super(context.getRepository());
    myConfig = config;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myRepository = context.getRepository();
    mySearchDepth = myConfig.getFixedSubmoduleCommitSearchDepth();
  }


  @Override
  public RevCommit next() throws IOException {
    myCurrentCommit = super.next();
    myNextCallCount++;
    if (myCurrentCommit != null && shouldLimitByNumberOfCommits() && myNextCallCount > myNumberOfCommitsToVisit) {
      myCurrentCommit = null;
    }
    return myCurrentCommit;
  }


  public void limitByNumberOfCommits(final int numberOfCommitsToVisit) {
    myNumberOfCommitsToVisit = numberOfCommitsToVisit;
  }


  @NotNull
  public ModificationData createModificationData() throws IOException, VcsException {
    checkCurrentCommit();

    final String commitId = myCurrentCommit.getId().name();
    String message = GitServerUtil.getFullMessage(myCurrentCommit);
    final PersonIdent authorIdent = GitServerUtil.getAuthorIdent(myCurrentCommit);
    final Date authorDate = authorIdent.getWhen();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Collecting changes in commit " + commitId + ":" + message + " (" + authorDate + ") for " + myGitRoot.debugInfo());
    }

    final String parentVersion = getFirstParentVersion(myCurrentCommit);
    final CommitChangesBuilder builder = new CommitChangesBuilder(myCurrentCommit, commitId, parentVersion);
    builder.collectCommitChanges();
    final List<VcsChange> changes = builder.getChanges();

    final String author = GitServerUtil.getUser(myGitRoot, authorIdent);
    final ModificationData result = new ModificationData(
      authorDate,
      changes,
      message,
      author,
      myGitRoot.getOriginalRoot(),
      commitId,
      commitId);

    Map<String, String> attributes = builder.getAttributes();
    if (!attributes.isEmpty())
      result.setAttributes(attributes);

    final PersonIdent commiterIdent = GitServerUtil.getCommitterIdent(myCurrentCommit);
    final String commiter = GitServerUtil.getUser(myGitRoot, commiterIdent);
    final Date commitDate = commiterIdent.getWhen();
    if (!Objects.equals(authorDate, commitDate)) {
      result.setAttribute("teamcity.commit.time", Long.toString(commitDate.getTime()));
    }
    if (!Objects.equals(author, commiter)) {
      result.setAttribute("teamcity.commit.user", commiter);
    }

    if (myCurrentCommit.getParentCount() > 0) {
      for (RevCommit parent : myCurrentCommit.getParents()) {
        parseBody(parent);
        result.addParentRevision(parent.getId().name());
      }
    } else {
      result.addParentRevision(ObjectId.zeroId().name());
    }
    return result;
  }

  private void checkCurrentCommit() {
    if (myCurrentCommit == null)
      throw new IllegalStateException("Current commit is null");
  }

  public boolean isIncludedByCheckoutRules(@NotNull CheckoutRules rules, @NotNull Set<RevCommit> uninterestingParents) throws VcsException, IOException {
    checkCurrentCommit();

    final RevCommit[] parents = myCurrentCommit.getParents();

    if (myCurrentCommit.getParentCount() > 1) {
      // merge commit is interesting only if more than one of it changes interesting files when comparing to both of its parents,
      // otherwise, if files are changed comparing to one parent only, then we need to go deeper through this parent
      // and find the actual commit which changed the files
      int numAffectedParents = 0;

      for (RevCommit parent: parents) {
        try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myRepository, myGitRoot.debugInfo(), myConfig.verboseTreeWalkLog())) {
          tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(myGitRoot));
          tw.setRecursive(true);
          myContext.addTree(myGitRoot, tw, myRepository, myCurrentCommit, true, false, rules);
          myContext.addTree(myGitRoot, tw, myRepository, parent, true, false, rules);

          while (tw.next()) {
            final String path = tw.getPathString();
            if (rules.shouldInclude(path)) {
              numAffectedParents++;
              break;
            }

            for (RevCommit p: parents) {
              if (p != parent) {
                uninterestingParents.add(p);
              }
            }
          }
        }
      }

      return numAffectedParents > 1;
    }

    try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myRepository, myGitRoot.debugInfo(), myConfig.verboseTreeWalkLog())) {
      tw.setFilter(new IgnoreSubmoduleErrorsTreeFilter(myGitRoot));
      tw.setRecursive(true);
      myContext.addTree(myGitRoot, tw, myRepository, myCurrentCommit, true, false, rules);
      for (RevCommit parent: parents) {
        myContext.addTree(myGitRoot, tw, myRepository, parent, true, false, rules);
      }

      while (tw.next()) {
        final String path = tw.getPathString();
        if (rules.shouldInclude(path)) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  public RevCommit getCurrentCommit() {
    checkCurrentCommit();
    return myCurrentCommit;
  }

  private boolean shouldLimitByNumberOfCommits() {
    return myNumberOfCommitsToVisit != -1;
  }


  private boolean shouldIgnoreSubmodulesErrors() {
    return myNextCallCount > 1;//ignore submodule errors for all commits excluding the first one
  }


  @NotNull
  private String getFirstParentVersion(@NotNull final RevCommit commit) throws IOException {
    final RevCommit[] parents = commit.getParents();
    if (parents.length == 0) {
      return ObjectId.zeroId().name();
    } else {
      RevCommit parent = parents[0];
      parseBody(parent);
      return parent.getId().name();
    }
  }

  private class CommitChangesBuilder {
    private final RevCommit commit;
    private final String currentVersion;
    private final String parentVersion;
    private final List<VcsChange> changes = new ArrayList<VcsChange>();
    private final Map<String, String> myAttributes = new HashMap<>();
    private final String repositoryDebugInfo = myGitRoot.debugInfo();
    private final IgnoreSubmoduleErrorsTreeFilter filter = new IgnoreSubmoduleErrorsTreeFilter(myGitRoot);
    private final Map<String, String> commitsWithFix = new HashMap<String, String>();

    /**
     * @param commit current commit
     * @param currentVersion teamcity version of current commit (sha@time)
     * @param parentVersion parent version to use in VcsChange objects
     */
    public CommitChangesBuilder(@NotNull final RevCommit commit,
                                @NotNull final String currentVersion,
                                @NotNull final String parentVersion) {
      this.commit = commit;
      this.currentVersion = currentVersion;
      this.parentVersion = parentVersion;
    }

    @NotNull
    public List<VcsChange> getChanges() {
      return changes;
    }

    @NotNull
    public Map<String, String> getAttributes() {
      return myAttributes;
    }

    /**
     * collect changes for the commit
     */
    public void collectCommitChanges() throws IOException, VcsException {
      final VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myRepository, repositoryDebugInfo, myConfig.verboseTreeWalkLog());
      try {
        tw.setFilter(filter);
        tw.setRecursive(true);
        myContext.addTree(myGitRoot, tw, myRepository, commit, shouldIgnoreSubmodulesErrors());
        RevCommit[] parents = commit.getParents();
        boolean reportPerParentChangedFiles = myConfig.reportPerParentChangedFiles() && parents.length > 1; // report only for merge commits
        for (RevCommit parentCommit : parents) {
          myContext.addTree(myGitRoot, tw, myRepository, parentCommit, true);
          if (reportPerParentChangedFiles) {
            tw.reportChangedFilesForParentCommit(parentCommit);
          }
        }

        new VcsChangesTreeWalker(tw).walk();

        if (reportPerParentChangedFiles) {
          Map<String, String> changedFilesAttributes = tw.buildChangedFilesAttributes();
          if (!changedFilesAttributes.isEmpty()) {
            myAttributes.putAll(changedFilesAttributes);
          }
        }
      } finally {
        tw.close();
      }
    }

    private class VcsChangesTreeWalker {
      private final VcsChangeTreeWalk tw;

      private VcsChangesTreeWalker(@NotNull final VcsChangeTreeWalk tw) {
        this.tw = tw;
      }

      private void walk() throws IOException, VcsException {
        while (tw.next()) {
          final String path = tw.getPathString();

          processChange(path);
        }
      }

      private void processChange(@NotNull final String path) throws IOException, VcsException {
        if (!myGitRoot.isCheckoutSubmodules()) {
          addVcsChange();
          return;
        }

        if (filter.isBrokenSubmoduleEntry(path)) {
          final RevCommit commitWithFix = getPreviousCommitWithFixedSubmodule(commit, path);
          commitsWithFix.put(path, commitWithFix == null ? null : commitWithFix.getId().name());
          if (commitWithFix != null) {
            subWalk(path, commitWithFix);
            return;
          }
        }

        if (filter.isChildOfBrokenSubmoduleEntry(path)) {
          final String brokenSubmodulePath = filter.getSubmodulePathForChildPath(path);
          final String commitWithFix = commitsWithFix.get(brokenSubmodulePath);
          if (commitWithFix != null) {
            return;
          }
        }

        addVcsChange();
      }

      private void subWalk(@NotNull final String path, @NotNull final RevCommit commitWithFix) throws IOException, VcsException {
        final VcsChangeTreeWalk tw2 = new VcsChangeTreeWalk(myRepository, repositoryDebugInfo, myConfig.verboseTreeWalkLog());
        try {
          tw2.setFilter(TreeFilter.ANY_DIFF);
          tw2.setRecursive(true);
          myContext.addTree(myGitRoot, tw2, myRepository, commit, true);
          myContext.addTree(myGitRoot, tw2, myRepository, commitWithFix, true);
          while (tw2.next()) {
            if (tw2.getPathString().startsWith(path + "/")) {
              addVcsChange(currentVersion, commitWithFix.getId().name(), tw2);
            }
          }
        } finally {
          tw2.close();
        }
      }

      private void addVcsChange() {
        addVcsChange(currentVersion, parentVersion, tw);
      }

      private void addVcsChange(@NotNull final String currentVersion,
                                @NotNull final String parentVersion,
                                @NotNull final VcsChangeTreeWalk tw) {
        final VcsChange change = tw.getVcsChange(currentVersion, parentVersion);
        if (change != null) changes.add(change);
      }
    }

    @Nullable
    private RevCommit getPreviousCommitWithFixedSubmodule(@NotNull final RevCommit fromCommit, @NotNull final String submodulePath)
      throws IOException, VcsException {
      if (mySearchDepth == 0)
        return null;

      final RevWalk revWalk = new RevWalk(myRepository);
      try {
        final RevCommit fromRev = revWalk.parseCommit(fromCommit.getId());
        revWalk.markStart(fromRev);
        revWalk.sort(RevSort.TOPO);

        RevCommit result = null;
        RevCommit prevRev;
        revWalk.next();
        int depth = 0;
        while (result == null && depth < mySearchDepth && (prevRev = revWalk.next()) != null) {
          depth++;
          final TreeWalk prevTreeWalk = new TreeWalk(myRepository);
          try {
            prevTreeWalk.setFilter(TreeFilter.ALL);
            prevTreeWalk.setRecursive(true);
            myContext.addTree(myGitRoot, prevTreeWalk, myRepository, prevRev, true, false, null);
            while(prevTreeWalk.next()) {
              String path = prevTreeWalk.getPathString();
              if (path.startsWith(submodulePath + "/")) {
                final SubmoduleAwareTreeIterator iter = prevTreeWalk.getTree(0, SubmoduleAwareTreeIterator.class);
                final SubmoduleAwareTreeIterator parentIter = iter.getParent();
                if (iter != null && !iter.isSubmoduleError() && parentIter != null && parentIter.isOnSubmodule()) {
                  result = prevRev;
                  break;
                }
              }
            }
          } finally {
            prevTreeWalk.close();
          }
        }
        return result;
      } finally {
        revWalk.close();
      }
    }
  }
}
