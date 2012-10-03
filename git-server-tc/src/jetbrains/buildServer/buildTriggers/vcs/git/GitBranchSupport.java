/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GitBranchSupport implements BranchSupport, GitServerExtension {

  private GitVcsSupport myGitSupport;

  public GitBranchSupport() {
  }

  public void setGitSupport(@NotNull GitVcsSupport gitSupport) {
    myGitSupport = gitSupport;
  }

  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }

  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot repository) throws VcsException {
    return myGitSupport.getCurrentState(repository);
  }

  @NotNull
  public Map<String, String> getBranchRootOptions(@NotNull VcsRoot original, @NotNull String branchName) {
    final Map<String, String> result = new HashMap<String, String>(original.getProperties());
    result.put(Constants.BRANCH_NAME, branchName);
    return result;
  }

  @Nullable
  public PersonalBranchDescription getPersonalBranchDescription(@NotNull VcsRoot original, @NotNull String branchName) throws VcsException {
    VcsRoot branchRoot = createBranchRoot(original, branchName);
    OperationContext context = myGitSupport.createContext(branchRoot, "find fork version");
    PersonalBranchDescription result = null;
    RevWalk walk = null;
    try {
      String originalCommit = myGitSupport.getCurrentVersion(original);
      String branchCommit   = myGitSupport.getCurrentVersion(branchRoot);
      myGitSupport.ensureCommitLoaded(context, context.getGitRoot(original), originalCommit);
      myGitSupport.ensureCommitLoaded(context, context.getGitRoot(branchRoot), branchCommit);

      Repository db = context.getRepository();
      walk = new RevWalk(db);
      walk.markStart(walk.parseCommit(ObjectId.fromString(branchCommit)));
      walk.markUninteresting(walk.parseCommit(ObjectId.fromString(originalCommit)));
      walk.sort(RevSort.TOPO);
      boolean lastCommit = true;
      String firstCommitInBranch = null;
      String lastCommitUser = null;
      RevCommit c;
      while ((c = walk.next()) != null) {
        if (lastCommit) {
          lastCommitUser = GitServerUtil.getUser(context.getGitRoot(), c);
          lastCommit = false;
        }
        firstCommitInBranch = c.name();
      }
      if (firstCommitInBranch != null && lastCommitUser != null)
        result = new PersonalBranchDescription(firstCommitInBranch, lastCommitUser);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      try {
        if (walk != null)
          walk.release();
      } finally {
        context.close();
      }
    }
    return result;
  }

  private VcsRoot createBranchRoot(VcsRoot original, String branchName) {
    VcsRootImpl result = new VcsRootImpl(original.getId(), original.getVcsName());
    result.addAllProperties(original.getProperties());
    result.addProperty(Constants.BRANCH_NAME, branchName);
    return result;
  }
}
