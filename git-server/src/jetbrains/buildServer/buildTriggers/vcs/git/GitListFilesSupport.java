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

import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class GitListFilesSupport implements ListDirectChildrenPolicy {

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final ServerPluginConfig myConfig;
  private String myCurrentRevision;
  private long myLastSyncTime = -1;

  public GitListFilesSupport(@NotNull GitVcsSupport vcs,
                             @NotNull CommitLoader commitLoader,
                             @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myConfig = config;
  }

  @NotNull
  public Collection<VcsFileData> listFiles(@NotNull VcsRoot root, @NotNull String path) throws VcsException {
    String currentVersion = getRevision(root);
    OperationContext context = myVcs.createContext(root, "list files");
    ListFilesTreeWalk walk = null;
    try {
      walk = getTreeWalk(context, path, currentVersion);
      List<VcsFileData> files = new ArrayList<VcsFileData>();
      while (walk.next()) {
        files.add(walk.getVcsFile());
      }
      return files;
    } catch (VcsFileNotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      if (walk != null)
        walk.release();
      context.close();
    }
  }

  @NotNull
  private ListFilesTreeWalk getTreeWalk(@NotNull OperationContext context, @NotNull String path, @NotNull String revision) throws Exception {
    Repository r = context.getRepository();
    RevCommit commit = myCommitLoader.loadCommit(context, context.getGitRoot(), revision);
    ListFilesTreeWalk walk;
    if (isRootPath(path)) {
      walk = new ListFilesTreeWalk(r);
      walk.addTree(commit.getTree());
    } else {
      TreeWalk walk1 = null;
      try {
        walk1 = TreeWalk.forPath(r, path, commit.getTree());
        if (walk1 == null)
          throw new VcsFileNotFoundException("Cannot find path " + path);
        if (walk1.getFileMode(0) == FileMode.GITLINK) //show submodules as empty dirs
          return new EmptyTreeWalk(r);
        ObjectId tree = walk1.getObjectId(0);
        walk = new ListFilesTreeWalk(r);
        walk.addTree(tree);
      } finally {
        if (walk1 != null)
          walk1.release();
      }
    }
    walk.setRecursive(false);
    return walk;
  }

  @NotNull
  private String getRevision(@NotNull VcsRoot root) throws VcsException {
    if (isOutOfDate()) {
      RepositoryStateData state = myVcs.getCurrentState(root);
      myCurrentRevision = GitUtils.versionRevision(state.getBranchRevisions().get(state.getDefaultBranchName()));
      myLastSyncTime = System.currentTimeMillis();
    }
    return myCurrentRevision;
  }

  private boolean isOutOfDate() {
    return myLastSyncTime == -1 ||
           myCurrentRevision == null ||
           System.currentTimeMillis() - myLastSyncTime > myConfig.getListFilesTTLSeconds() * 1000;
  }

  private boolean isRootPath(@Nullable String path) {
    return StringUtil.isEmpty(path);
  }

  private static class ListFilesTreeWalk extends TreeWalk {

    ListFilesTreeWalk(Repository r) {
      super(r);
    }

    @NotNull
    VcsFileData getVcsFile() {
      String name = getNameString();
      FileMode mode = getFileMode(0);
      if (mode == FileMode.MISSING)
        throw new IllegalStateException("Missing file " + name);
      if (mode == FileMode.TREE)
        return new VcsFileData(name, true);
      if (mode == FileMode.GITLINK)
        return new VcsFileData(name, true);
      if (mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE || mode == FileMode.SYMLINK)
        return new VcsFileData(name, false);
      throw new IllegalStateException("Unknown file mode: " + mode + ", path " + name);
    }
  }

  private static class EmptyTreeWalk extends ListFilesTreeWalk {

    private EmptyTreeWalk(Repository repo) {
      super(repo);
    }

    @Override
    public boolean next() throws IOException {
      return false;
    }
  }
}
