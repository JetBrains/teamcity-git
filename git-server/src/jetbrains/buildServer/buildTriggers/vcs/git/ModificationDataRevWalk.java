/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  public RevCommit next() throws MissingObjectException, IncorrectObjectTypeException, IOException {
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
    if (myCurrentCommit == null)
      throw new IllegalStateException("Current commit is null");

    final String commitId = myCurrentCommit.getId().name();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Collecting changes in commit " + commitId + ":" + myCurrentCommit.getShortMessage() +
                " (" + myCurrentCommit.getCommitterIdent().getWhen() + ") for " + myGitRoot.debugInfo());
    }

    final String parentVersion = getFirstParentVersion(myCurrentCommit);
    final List<VcsChange> changes = new CommitChangesBuilder().getCommitChanges(myCurrentCommit, commitId, parentVersion);

    final PersonIdent authorIdent = myCurrentCommit.getAuthorIdent();
    final ModificationData result = new ModificationData(
      authorIdent.getWhen(),
      changes,
      myCurrentCommit.getFullMessage(),
      GitServerUtil.getUser(myGitRoot, authorIdent),
      myGitRoot.getOriginalRoot(),
      commitId,
      commitId);

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
    /**
     * Get changes for the commit
     *
     * @param commit current commit
     * @param currentVersion teamcity version of current commit (sha@time)
     * @param parentVersion parent version to use in VcsChange objects
     * @return the commit changes
     */
    @NotNull
    private List<VcsChange> getCommitChanges(@NotNull final RevCommit commit,
                                             final String currentVersion,
                                             final String parentVersion) throws IOException, VcsException {
      final List<VcsChange> changes = new ArrayList<VcsChange>();
      final String repositoryDebugInfo = myGitRoot.debugInfo();
      final VcsChangeTreeWalk tw = new VcsChangeTreeWalk(myConfig, myRepository, repositoryDebugInfo);
      try {
        final IgnoreSubmoduleErrorsTreeFilter filter = new IgnoreSubmoduleErrorsTreeFilter(myGitRoot);
        tw.setFilter(filter);
        tw.setRecursive(true);
        myContext.addTree(myGitRoot, tw, myRepository, commit, shouldIgnoreSubmodulesErrors());
        for (RevCommit parentCommit : commit.getParents()) {
          myContext.addTree(myGitRoot, tw, myRepository, parentCommit, true);
        }
        final Map<String, RevCommit> commitsWithFix = new HashMap<String, RevCommit>();
        while (tw.next()) {
          final String path = tw.getPathString();

          if (!myGitRoot.isCheckoutSubmodules()) {
            addVcsChange(changes, currentVersion, parentVersion, tw);
            continue;
          }

          if (filter.isBrokenSubmoduleEntry(path)) {
            final RevCommit commitWithFix = getPreviousCommitWithFixedSubmodule(commit, path);
            commitsWithFix.put(path, commitWithFix);
            if (commitWithFix != null) {
              VcsChangeTreeWalk tw2 = new VcsChangeTreeWalk(myConfig, myRepository, repositoryDebugInfo);
              try {
                tw2.setFilter(TreeFilter.ANY_DIFF);
                tw2.setRecursive(true);
                myContext.addTree(myGitRoot, tw2, myRepository, commit, true);
                myContext.addTree(myGitRoot, tw2, myRepository, commitWithFix, true);
                while (tw2.next()) {
                  if (tw2.getPathString().equals(path)) {
                    addVcsChange(changes, currentVersion, commitWithFix.getId().name(), tw2);
                  }
                }
              } finally {
                tw2.release();
              }
            } else {
              addVcsChange(changes, currentVersion, parentVersion, tw);
            }
          } else if (filter.isChildOfBrokenSubmoduleEntry(path)) {
            final String brokenSubmodulePath = filter.getSubmodulePathForChildPath(path);
            final RevCommit commitWithFix = commitsWithFix.get(brokenSubmodulePath);
            if (commitWithFix != null) {
              VcsChangeTreeWalk tw2 = new VcsChangeTreeWalk(myConfig, myRepository, repositoryDebugInfo);
              try {
                tw2.setFilter(TreeFilter.ANY_DIFF);
                tw2.setRecursive(true);
                myContext.addTree(myGitRoot, tw2, myRepository, commit, true);
                myContext.addTree(myGitRoot, tw2, myRepository, commitWithFix, true);
                while (tw2.next()) {
                  if (tw2.getPathString().equals(path)) {
                    addVcsChange(changes, currentVersion, commitWithFix.getId().name(), tw2);
                  }
                }
              } finally {
                tw2.release();
              }
            } else {
              addVcsChange(changes, currentVersion, parentVersion, tw);
            }
          } else {
            addVcsChange(changes, currentVersion, parentVersion, tw);
          }
        }
        return changes;
      } finally {
        tw.release();
      }
    }
  }

  private void addVcsChange(@NotNull final List<VcsChange> changes, String currentVersion, String parentVersion, @NotNull final VcsChangeTreeWalk tw) {
    VcsChange change = tw.getVcsChange(currentVersion, parentVersion);
    if (change != null)
      changes.add(change);
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
        TreeWalk prevTreeWalk = new TreeWalk(myRepository);
        try {
          prevTreeWalk.setFilter(TreeFilter.ALL);
          prevTreeWalk.setRecursive(true);
          myContext.addTree(myGitRoot, prevTreeWalk, myRepository, prevRev, true, false);
          while(prevTreeWalk.next()) {
            String path = prevTreeWalk.getPathString();
            if (path.startsWith(submodulePath + "/")) {
              SubmoduleAwareTreeIterator iter = prevTreeWalk.getTree(0, SubmoduleAwareTreeIterator.class);
              SubmoduleAwareTreeIterator parentIter = iter.getParent();
              if (iter != null && !iter.isSubmoduleError() && parentIter != null && parentIter.isOnSubmodule()) {
                result = prevRev;
                break;
              }
            }
          }
        } finally {
          prevTreeWalk.release();
        }
      }
      return result;
    } finally {
      revWalk.release();
    }
  }
}
