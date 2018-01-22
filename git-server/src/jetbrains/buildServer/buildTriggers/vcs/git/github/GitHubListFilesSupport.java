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

package jetbrains.buildServer.buildTriggers.vcs.git.github;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.*;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.Tree;
import org.eclipse.egit.github.core.TreeEntry;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DataService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GitHubListFilesSupport implements ListDirectChildrenPolicy {

  private final GitVcsSupport myVcs;
  private final GitListFilesSupport myGenericListFiles;
  private final String myOwner;
  private final String myRepository;
  private Tree myTree;

  public GitHubListFilesSupport(@NotNull GitVcsSupport vcs,
                                @NotNull GitListFilesSupport genericListFiles,
                                @NotNull String owner,
                                @NotNull String repository) {
    myVcs = vcs;
    myGenericListFiles = genericListFiles;
    myOwner = owner;
    myRepository = repository;
  }

  @NotNull
  public Collection<VcsFileData> listFiles(@NotNull VcsRoot root, @NotNull String directoryPath) throws VcsException {
    OperationContext ctx = myVcs.createContext(root, "list files");
    try {
      if (myTree == null)
        loadTree(ctx, root);

      List<VcsFileData> files = new ArrayList<VcsFileData>();
      for (TreeEntry e : myTree.getTree()) {
        String fullPath = e.getPath();
        String directChildName = getDirectChildName(directoryPath, fullPath);
        if (directChildName != null)
          files.add(new VcsFileData(directChildName, "tree".equals(e.getType())));
      }
      return files;
    } catch (Exception e) {
      //LOG
      return myGenericListFiles.listFiles(root, directoryPath);
    } finally {
      ctx.close();
    }
  }

  @Nullable
  private String getDirectChildName(@NotNull String parentDir, @NotNull String path) {
    if (!isUnder(parentDir, path))
      return null;
    if (parentDir.equals("") && !path.contains("/"))
      return path;
    String subPath = path.substring(parentDir.length() + 1);
    if (subPath.contains("/"))
      return null;
    return subPath;
  }

  private boolean isUnder(@NotNull String parentDir, @NotNull String path) {
    if (path.length() <= parentDir.length())
      return false;
    return path.startsWith(parentDir);
  }

  private void loadTree(@NotNull OperationContext ctx, @NotNull VcsRoot root) throws IOException, VcsException {
    GitHubClient client = new GitHubClient();
    GitVcsRoot gitRoot = ctx.getGitRoot(root);
    AuthSettings auth = gitRoot.getAuthSettings();
    if (auth.getAuthMethod() == AuthenticationMethod.PASSWORD && auth.getUserName() != null && auth.getPassword() != null) {
      client.setCredentials(auth.getUserName(), auth.getPassword());
    }
    Repository r = new RepositoryService(client).getRepository(myOwner, myRepository);
    RepositoryStateData state = myVcs.getCurrentState(root);
    String defaultBranchRevision = state.getBranchRevisions().get(state.getDefaultBranchName());
    myTree = new DataService(client).getTree(r, defaultBranchRevision, true);
  }
}
