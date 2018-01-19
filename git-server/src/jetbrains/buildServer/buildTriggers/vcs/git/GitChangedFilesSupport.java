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

import jetbrains.buildServer.vcs.ChangedFilesSupport;
import jetbrains.buildServer.vcs.VcsChangeInfo;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class GitChangedFilesSupport implements ChangedFilesSupport, GitServerExtension {
  private final GitVcsSupport myVcs;
  private final RepositoryManager myRepositoryManager;
  private final CommitLoader myCommitLoader;
  public GitChangedFilesSupport(@NotNull GitVcsSupport vcs,
                                @NotNull RepositoryManager repositoryManager,
                                @NotNull CommitLoader commitLoader) {
    myVcs = vcs;
    myRepositoryManager = repositoryManager;
    myCommitLoader = commitLoader;
  }

  @Override
  public void computeChangedFiles(@NotNull VcsRoot root, @NotNull String c1, @NotNull String c2, @NotNull ChangedFilesConsumer consumer) throws VcsException {
    ChangedFilesReporter reporter = null;
    try {
      reporter = createChangedFilesReporter(root);
      reporter.computeChangedFiles(c1, c2, consumer);
    } finally {
      if (reporter != null)
        reporter.close();
    }
  }


  @NotNull
  @Override
  public ChangedFilesReporter createChangedFilesReporter(@NotNull VcsRoot root) throws VcsException {
    OperationContext context = myVcs.createContext(root, "compute changed files");
    GitVcsRoot gitRoot = context.getGitRoot();
    return new GitChangedFilesReporter(myRepositoryManager, myCommitLoader, context, gitRoot);
  }


  private static class GitChangedFilesReporter implements ChangedFilesSupport.ChangedFilesReporter {
    private final RepositoryManager myRepositoryManager;
    private final CommitLoader myCommitLoader;
    private final OperationContext myContext;
    private final GitVcsRoot myGitVcsRoot;
    private boolean myFetchInvoked = false;

    GitChangedFilesReporter(@NotNull RepositoryManager repositoryManager,
                            @NotNull CommitLoader commitLoader,
                            @NotNull OperationContext context,
                            @NotNull GitVcsRoot gitRoot) {
      myRepositoryManager = repositoryManager;
      myCommitLoader = commitLoader;
      myContext = context;
      myGitVcsRoot = gitRoot;
    }

    @Override
    public void computeChangedFiles(@NotNull String c1, @NotNull String c2, @NotNull ChangedFilesConsumer consumer) throws VcsException {
      myRepositoryManager.runWithDisabledRemove(myGitVcsRoot.getRepositoryDir(), () -> {
        Repository r = myContext.getRepository();
        RevCommit commit1 = ensureCommitLoaded(myGitVcsRoot, r, c1);
        RevCommit commit2 = ensureCommitLoaded(myGitVcsRoot, r, c2);
        reportChanges(r, commit1, commit2, consumer);
      });
    }

    private void reportChanges(@NotNull Repository r, @NotNull RevCommit c1, @NotNull RevCommit c2, @NotNull ChangedFilesConsumer consumer) throws VcsException {
      TreeWalk walk = null;
      try {
        walk = new TreeWalk(r);
        walk.setRecursive(true);
        walk.setFilter(TreeFilter.ANY_DIFF);
        walk.addTree(c1.getTree());
        walk.addTree(c2.getTree());
        while (walk.next()) {
          String path = walk.getPathString();
          if (!consumer.consume(path, getChangeType(walk)))
            break;
        }
      } catch (Exception e) {
        throw new VcsException("Error while comparing commits " + c1.name() + " and " + c2.name() + ": " + e.getMessage(), e);
      } finally {
        if (walk != null)
          walk.release();
      }
    }


    @NotNull
    private VcsChangeInfo.Type getChangeType(@NotNull TreeWalk walk) {
      VcsChangeInfo.Type result = VcsChangeInfo.Type.CHANGED;
      if (walk.getFileMode(0) == FileMode.MISSING) {
        result = VcsChangeInfo.Type.ADDED;
      } else if (walk.getFileMode(1) == FileMode.MISSING) {
        result = VcsChangeInfo.Type.REMOVED;
      }
      return result;
    }


    @NotNull
    private RevCommit ensureCommitLoaded(@NotNull GitVcsRoot root, @NotNull Repository db, @NotNull String commit) throws VcsException {
      try {
        RevCommit result = myCommitLoader.findCommit(db, commit);
        if (result == null && !myFetchInvoked) {
          Set<RefSpec> spec = Collections.singleton(new RefSpec("refs/*:refs/*").setForceUpdate(true));
          myCommitLoader.fetch(db, root.getRepositoryFetchURL(), spec, new FetchSettings(root.getAuthSettings()));
          myFetchInvoked = true;
        }
        result = myCommitLoader.findCommit(db, commit);
        if (result == null)
          throw new VcsException("Cannot find commit " + commit);
        return result;
      } catch (Exception e) {
        if (e instanceof VcsException)
          throw (VcsException)e;
        throw new VcsException("Error while fetching repository", e);
      }
    }


    @Override
    public void close() {
      myContext.close();
    }
  }
}
