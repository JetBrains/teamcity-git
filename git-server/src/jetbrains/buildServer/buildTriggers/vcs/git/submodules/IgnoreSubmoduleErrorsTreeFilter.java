/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Tree filter that ignore changes if they arouse because of errors in submodules in the first tree (i.e. in the first commit)
 */
public class IgnoreSubmoduleErrorsTreeFilter extends TreeFilter {

  private final Settings mySettings;
  private final Set<String> myBrokenSubmodulePathsInFirstTree = new HashSet<String>();
  private final Set<String> myBrokenSubmodulePathsInRestTrees = new HashSet<String>();

  public IgnoreSubmoduleErrorsTreeFilter(Settings settings) {
    mySettings = settings;
  }

  @Override
  public boolean include(TreeWalk walker) throws IOException {
    if (mySettings.isCheckoutSubmodules()) {
      String path = walker.getPathString();
      if (isFirstTreeHasBrokenSubmodule(walker, path)) {
        myBrokenSubmodulePathsInFirstTree.add(path);
        return false;
      } else if (!myBrokenSubmodulePathsInRestTrees.contains(path)) {
        for (int i = 1; i < walker.getTreeCount(); i++) {
          if (FileMode.GITLINK.equals(walker.getRawMode(i))) {
            myBrokenSubmodulePathsInRestTrees.add(path);
          }
        }
      }
      return TreeFilter.ANY_DIFF.include(walker);
    } else {
      return TreeFilter.ANY_DIFF.include(walker);
    }
  }

  private boolean isFirstTreeHasBrokenSubmodule(TreeWalk walker, String path) {
    return FileMode.GITLINK.equals(walker.getRawMode(0)) || myBrokenSubmodulePathsInFirstTree.contains(path);
  }

  public Set<String> getBrokenSubmodulePathsInRestTrees() {
    return myBrokenSubmodulePathsInRestTrees;
  }

  public boolean isBrokenSubmodulePath(String path) {
    for (String brokenSubmodulePath : myBrokenSubmodulePathsInRestTrees) {
      if (path.equals(brokenSubmodulePath) || path.startsWith(brokenSubmodulePath + "/")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean shouldBeRecursive() {
    return TreeFilter.ANY_DIFF.shouldBeRecursive();
  }

  @Override
  public TreeFilter clone() {
    return this;
  }

  @Override
  public String toString() {
    return "IGNORE_SUBMODULE_ERRORS";
  }
}
