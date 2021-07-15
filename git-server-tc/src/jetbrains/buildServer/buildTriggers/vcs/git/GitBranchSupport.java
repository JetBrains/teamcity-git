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

import jetbrains.buildServer.vcs.BranchSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

//todo clear this class
public class GitBranchSupport implements BranchSupport, GitServerExtension {
  private final GitVcsSupport myVcs;

  public GitBranchSupport(GitVcsSupport vcs) {
    myVcs = vcs;
    myVcs.addExtension(this);
  }

  public void createBranch(@NotNull VcsRoot root,
                           @NotNull String newBranchName) throws VcsException  {
    OperationContext context = myVcs.createContext(root, "branchCreation");
    Repository db = context.getRepository();
    System.out.println("REPOSITORY: " + db.toString());
  }

  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }
}
