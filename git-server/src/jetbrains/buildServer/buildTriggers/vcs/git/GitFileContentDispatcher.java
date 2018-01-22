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

import jetbrains.buildServer.buildTriggers.vcs.git.github.GitHubRawFileContentProvider;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;

public class GitFileContentDispatcher implements VcsFileContentProvider {

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final ServerPluginConfig myConfig;

  private VcsFileContentProvider myImplementation;

  public GitFileContentDispatcher(@NotNull GitVcsSupport vcs,
                                  @NotNull CommitLoader commitLoader,
                                  @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myConfig = config;
  }

  @NotNull
  public byte[] getContent(@NotNull VcsModification vcsModification,
                           @NotNull VcsChangeInfo change,
                           @NotNull VcsChangeInfo.ContentType contentType,
                           @NotNull VcsRoot root) throws VcsException {
    if (myImplementation == null)
      myImplementation = getContentProvider(root);
    return myImplementation.getContent(vcsModification, change, contentType, root);
  }

  @NotNull
  public byte[] getContent(@NotNull String filePath, @NotNull VcsRoot root, @NotNull String version) throws VcsException {
    synchronized (this) {
      if (myImplementation == null)
        myImplementation = getContentProvider(root);
    }
    return myImplementation.getContent(filePath, root, version);
  }

  private VcsFileContentProvider getContentProvider(@NotNull VcsRoot root) {
    OperationContext ctx = myVcs.createContext(root, "file content dispatch");
    GitVcsFileContentProvider genericProvider = new GitVcsFileContentProvider(myVcs, myCommitLoader, myConfig);
    try {
      if (GitServerUtil.isCloned(ctx.getRepository()))
        return genericProvider;
      VcsHostingRepo ghRepo = WellKnownHostingsUtil.getGitHubRepo(ctx.getGitRoot().getRepositoryFetchURL());
      if (ghRepo == null)
        return genericProvider;
      return new GitHubRawFileContentProvider(myVcs, genericProvider, ghRepo.owner(), ghRepo.repoName());
    } catch (Exception e) {
      //LOG
      return genericProvider;
    } finally {
      ctx.close();
    }
  }
}
