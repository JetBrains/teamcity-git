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

package jetbrains.buildServer.buildTriggers.vcs.git.browse;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
* @author dmitry.neverov
*/
public class Dir extends GitDirElement {

  private final GitVcsSupport myGit;
  private final VcsRoot myRoot;
  private final String myRevision;

  public Dir(@NotNull GitVcsSupport git, @NotNull VcsRoot root, @NotNull String revision, @NotNull File dir) {
    super(dir);
    myGit = git;
    myRoot = root;
    myRevision = revision;
  }

  public Iterable<Element> getChildren() {
    OperationContext context = myGit.createContext(myRoot, "list files");
    TreeWalk walk = null;
    try {
      walk = getTreeWalk(context);
      List<Element> children = new ArrayList<Element>();
      while (walk.next()) {
        String path = walk.getPathString();
        FileMode fileMode = walk.getFileMode(0);
        children.add(makeChild(path, fileMode));
      }
      return children;
    } catch (Exception e) {
      throw new BrowserException(e);
    } finally {
      if (walk != null)
        walk.release();
      context.close();
    }
  }

  @NotNull
  private Element makeChild(@NotNull String path, @NotNull FileMode mode) {
    if (mode == FileMode.MISSING)
      throw new IllegalStateException("Missing file " + path);
    if (mode == FileMode.TREE)
      return new Dir(myGit, myRoot, myRevision, getFile(path));
    if (mode == FileMode.GITLINK)
      return new EmptyDir(getFile(path));
    if (mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE || mode == FileMode.SYMLINK)
      return new GitFile(myGit, myRoot, myRevision, getFile(path));
    throw new IllegalStateException("Unknown file mode: " + mode + ", path " + path);
  }

  @NotNull
  private TreeWalk getTreeWalk(@NotNull OperationContext context) throws IOException, VcsException {
    Repository r = context.getRepository();
    RevCommit commit = myGit.getCommit(r, myRevision);
    TreeWalk walk;
    if (isRootDir()) {
      walk = new TreeWalk(r);
      walk.addTree(commit.getTree());
    } else {
      TreeWalk walk1 = TreeWalk.forPath(r, myDir.getPath(), commit.getTree());
      ObjectId tree = walk1.getObjectId(0);
      walk = new TreeWalk(r);
      walk.addTree(tree);
    }
    walk.setRecursive(false);
    return walk;
  }

  @NotNull
  private File getFile(@NotNull String path) {
    String parent = myDir.getPath();
    if (isRootPath(parent)) {
      return new File(path);
    } else {
      return new File(parent, path);
    }
  }

  private boolean isRootDir() {
    return isRootPath(myDir.getPath());
  }

  private boolean isRootPath(@Nullable String path) {
    return StringUtil.isEmpty(path);
  }
}
