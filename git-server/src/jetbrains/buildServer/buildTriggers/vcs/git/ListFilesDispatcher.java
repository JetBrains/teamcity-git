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

import jetbrains.buildServer.buildTriggers.vcs.git.github.GitHubListFilesSupport;
import jetbrains.buildServer.vcs.ListDirectChildrenPolicy;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsFileData;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ListFilesDispatcher implements ListDirectChildrenPolicy {

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final ServerPluginConfig myConfig;
  private ListDirectChildrenPolicy myImplementation;

  public ListFilesDispatcher(@NotNull GitVcsSupport vcs,
                             @NotNull CommitLoader commitLoader,
                             @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myConfig = config;
  }

  @NotNull
  public Collection<VcsFileData> listFiles(@NotNull VcsRoot root, @NotNull String directoryPath) throws VcsException {
    synchronized (this) {
      if (myImplementation == null)
        myImplementation = getPolicy(root);
    }
    return myImplementation.listFiles(root, directoryPath);
  }

  private ListDirectChildrenPolicy getPolicy(@NotNull VcsRoot root) throws VcsException {
    OperationContext ctx = myVcs.createContext(root, "list files dispatch");
    GitListFilesSupport genericListFiles = new GitListFilesSupport(myVcs, myCommitLoader, myConfig);
    try {
      if (GitServerUtil.isCloned(ctx.getRepository()))
        return genericListFiles;
      VcsHostingRepo ghRepo = WellKnownHostingsUtil.getGitHubRepo(ctx.getGitRoot().getRepositoryFetchURL());
      if (ghRepo == null)
        return genericListFiles;
      return new GitHubListFilesSupport(myVcs, genericListFiles, ghRepo.owner(), ghRepo.repoName());
    } catch (Exception e) {
      //LOG
      return genericListFiles;
    } finally {
      ctx.close();
    }
  }
}
