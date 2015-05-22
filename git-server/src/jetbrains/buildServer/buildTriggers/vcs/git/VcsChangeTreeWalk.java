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
import jetbrains.buildServer.vcs.VcsChange;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class VcsChangeTreeWalk extends TreeWalk {

  private static final Logger LOG = Logger.getInstance(VcsChangeTreeWalk.class.getName());

  private final String myRepositoryDebugInfo;
  private final boolean myVerboseTreeWalkLog;

  public VcsChangeTreeWalk(@NotNull ObjectReader repo,
                           @NotNull String repositoryDebugInfo,
                           boolean verboseTreeWalkLog) {
    super(repo);
    myRepositoryDebugInfo = repositoryDebugInfo;
    myVerboseTreeWalkLog = verboseTreeWalkLog;
  }

  public VcsChangeTreeWalk(@NotNull Repository repo,
                           @NotNull String repositoryDebugInfo,
                           boolean verboseTreeWalkLog) {
    super(repo);
    myRepositoryDebugInfo = repositoryDebugInfo;
    myVerboseTreeWalkLog = verboseTreeWalkLog;
  }


  @Nullable
  VcsChange getVcsChange(String currentVersion, String parentVersion) {
    final String path = getPathString();
    final ChangeType gitChangeType = classifyChange();

    if (isExtraDebug())
      LOG.debug("Processing change " + treeWalkInfo(path) + " as " + gitChangeType + " " + myRepositoryDebugInfo);

    VcsChange.Type type = getChangeType(gitChangeType, path);
    if (type == VcsChange.Type.NOT_CHANGED) {
      return null;
    } else {
      String description = gitChangeType == ChangeType.FILE_MODE_CHANGED ? "File mode changed" : null;
      return new VcsChange(type, description, path, path, parentVersion, currentVersion);
    }
  }


  /**
   * Classify change in tree walker. The first tree is assumed to be a current commit and other
   * trees are assumed to be parent commits. In the case of multiple changes, the changes that
   * come from at lease one parent commit are assumed to be reported in the parent commit.
   * @return change type
   */
  @NotNull
  public ChangeType classifyChange() {
    final FileMode mode0 = getFileMode(0);
    if (isExtraDebug())
      LOG.debug(getPathString() + " file mode: " + mode0);
    if (FileMode.MISSING.equals(mode0)) {
      for (int i = 1; i < getTreeCount(); i++) {
        if (FileMode.MISSING.equals(getFileMode(i))) {
          // the delete merge
          return ChangeType.UNCHANGED;
        }
      }
      return ChangeType.DELETED;
    }
    boolean fileAdded = true;
    for (int i = 1; i < getTreeCount(); i++) {
      if (!FileMode.MISSING.equals(getFileMode(i))) {
        fileAdded = false;
        break;
      }
    }
    if (fileAdded) {
      return ChangeType.ADDED;
    }
    boolean fileModified = true;
    for (int i = 1; i < getTreeCount(); i++) {
      if (idEqual(0, i)) {
        fileModified = false;
        break;
      }
    }
    if (fileModified) {
      return ChangeType.MODIFIED;
    }
    int modeBits0 = mode0.getBits();
    boolean fileModeModified = true;
    for (int i = 1; i < getTreeCount(); i++) {
      int modeBits = getFileMode(i).getBits();
      if (modeBits == modeBits0) {
        fileModeModified = false;
        break;
      }
    }
    if (fileModeModified) {
      return ChangeType.FILE_MODE_CHANGED;
    }
    return ChangeType.UNCHANGED;
  }

  private boolean isExtraDebug() {
    return LOG.isDebugEnabled() && myVerboseTreeWalkLog;
  }


  private VcsChange.Type getChangeType(ChangeType gitChangeType, String path) {
    switch (gitChangeType) {
      case UNCHANGED:
        return VcsChange.Type.NOT_CHANGED;
      case ADDED:
        return VcsChange.Type.ADDED;
      case DELETED:
        TreeFilter filter = getFilter();
        if (filter instanceof IgnoreSubmoduleErrorsTreeFilter && ((IgnoreSubmoduleErrorsTreeFilter) filter).getBrokenSubmodulePathsInRestTrees().contains(path)) {
          return VcsChange.Type.NOT_CHANGED;
        } else {
          return VcsChange.Type.REMOVED;
        }
      case MODIFIED:
        return VcsChange.Type.CHANGED;
      case FILE_MODE_CHANGED:
        return VcsChange.Type.CHANGED;
      default:
        throw new IllegalStateException("Unknown change type");
    }
  }


  public String treeWalkInfo(final String path) {
    StringBuilder b = new StringBuilder();
    b.append(path);
    b.append('(');
    final int n = getTreeCount();
    for (int i = 0; i < n; i++) {
      if (i != 0) {
        b.append(", ");
      }
      b.append(getObjectId(i).name());
      b.append(String.format("%04o", getFileMode(i).getBits()));
    }
    b.append(')');
    return b.toString();
  }


  /**
   * Get difference in the file mode (passed to chmod), null if there is no difference
   * @return see above
   */
  public String getModeDiff() {
    boolean cExec = isExecutable(getFileMode(0));
    boolean pExec = isExecutable(getFileMode(1));
    String mode;
    if (cExec & !pExec) {
      mode = "a+x";
    } else if (!cExec & pExec) {
      mode = "a-x";
    } else {
      mode = null;
    }
    return mode;
  }


  /**
   * Check if the file mode is executable
   *
   * @param m file mode to check
   * @return true if the file is executable
   */
  private boolean isExecutable(FileMode m) {
    return (m.getBits() & (1 << 6)) != 0;
  }
}
