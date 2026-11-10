

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.vcs.VcsChange;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class VcsChangeTreeWalk extends TreeWalk {

  private static final Logger LOG = Logger.getInstance(VcsChangeTreeWalk.class.getName());
  public static final String CHANGED_FILES_ATTR_PREFIX = "teamcity.transient.changedFiles.";

  private final String myRepositoryDebugInfo;
  private final boolean myVerboseTreeWalkLog;

  private final List<String> myParentCommits = new ArrayList<>(0);
  private final Map<String, List<String>> myPerParentChangedFiles = new HashMap<>();
  private final Set<String> myAllFilesChangedParents = new HashSet<>();

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


  public void reportChangedFilesForParentCommit(@NotNull RevCommit parentCommit) {
    myParentCommits.add(parentCommit.name());
    myPerParentChangedFiles.put(parentCommit.name(), new ArrayList<>());
  }

  /**
   * Reports all the files of the commit as changed against the parent, without collecting them;
   * use when the parent has no files in common with the commit, see {@link #hasAnyCommonFiles}.
   */
  public void reportAllFilesChangedForParentCommit(@NotNull RevCommit parentCommit) {
    // keeps the tree index -> parent mapping used by fillPerParentChangedFiles,
    // which skips parents without a list in myPerParentChangedFiles
    myParentCommits.add(parentCommit.name());
    myAllFilesChangedParents.add(parentCommit.name());
  }

  public Map<String, String> buildChangedFilesAttributes() {
    Map<String, String> result = buildChangedFilesAttributesFor(myPerParentChangedFiles);
    if (myAllFilesChangedParents.isEmpty()) return result;

    result = new HashMap<>(result);
    for (String parentCommit : myAllFilesChangedParents) {
      result.put(CHANGED_FILES_ATTR_PREFIX + parentCommit, "**/*");
    }
    return result;
  }


  /**
   * Checks whether the commit tree has at least one entry, a file or a whole directory, with the same object id and mode
   * at the same path in the parent tree. When it does not, every path of the commit differs from the parent; for a merge
   * commit this is the case of a parent with unrelated history, e.g. a separate repository merged into a monorepo.
   * <p>
   * Equal tree ids mean equal directories all the way down, so the walk stops at the first equal entry and descends
   * only into directories present in both trees with different ids.
   *
   * Equal trees, including two empty ones, count as having common files: the edge between them has no differences.
   * <p>
   * A submodule present in both trees, or replacing a file or a directory, also counts as a common file: with submodule
   * checkout the changes collection compares the files of the submodule, which cannot be compared using gitlinks only.
   * <p>
   * The check runs in a separate walk sharing the reader of this one, the reader stays open when that walk is closed.
   *
   * @param commit the commit, its tree must be parsed
   * @param parentCommit one of its parents, its tree must be parsed
   * @return true if the trees are equal, share at least one file or directory, or can share files of a submodule
   */
  public boolean hasAnyCommonFiles(@NotNull RevCommit commit, @NotNull RevCommit parentCommit) throws IOException {
    // without this check two empty trees have no entries to compare and would be reported as having no common files
    if (commit.getTree().equals(parentCommit.getTree())) return true;

    // matches entries with the same name and different types, e.g. a submodule replacing a directory, in one step
    try (TreeWalk tw = new NameConflictTreeWalk(getObjectReader())) {
      tw.addTree(commit.getTree());
      tw.addTree(parentCommit.getTree());
      tw.setRecursive(false);
      while (tw.next()) {
        // modes are compared the same way as in fillPerParentChangedFiles, which builds the list this check stands in for:
        // getFileMode folds non-canonical modes into the canonical ones, e.g. 100664 into REGULAR_FILE
        FileMode commitMode = tw.getFileMode(0);
        if (FileMode.MISSING.equals(commitMode)) continue; // the path exists in the parent only

        FileMode parentMode = tw.getFileMode(1);
        if (tw.idEqual(0, 1) && Objects.equals(commitMode, parentMode)) return true; // the same file or the same directory

        if (!FileMode.MISSING.equals(parentMode) && (FileMode.GITLINK.equals(commitMode) || FileMode.GITLINK.equals(parentMode))) {
          // with submodule checkout the changes collection expands gitlinks into the files of the submodules,
          // they can have common files which cannot be detected here, so assume they do
          return true;
        }

        if (FileMode.TREE.equals(commitMode) && FileMode.TREE.equals(parentMode)) {
          tw.enterSubtree();
        }
        // a directory present in the commit tree only consists of new files, no need to descend
      }
      return false;
    }
  }


  @NotNull
  public static Map<String, String> buildChangedFilesAttributesFor(@NotNull Map<String, ? extends Collection<String>> perParentChangedFiles) {
    if (perParentChangedFiles.isEmpty())
      return Collections.emptyMap();
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, ? extends Collection<String>> entry : perParentChangedFiles.entrySet()) {
      String parentCommit = entry.getKey();
      Collection<String> files = entry.getValue();
      Map<String, List<String>> groupedByCommonPrefix = new HashMap<>();
      for (String path: files) {
        int separatorIdx = path.lastIndexOf('/');
        String prefix = "";
        String fileName = path;
        if (separatorIdx != -1) {
          prefix = path.substring(0, separatorIdx + 1);
          fileName = path.substring(separatorIdx+1);
        }

        final String finalFileName = fileName;
        groupedByCommonPrefix.compute(prefix, (k, v) -> {
          if (v == null) {
            v = new ArrayList<>();
          }
          v.add(finalFileName);
          return v;
        });
      }

      StringBuilder value = new StringBuilder();
      for (String path: groupedByCommonPrefix.keySet()) {
        if (path.length() > 0) {
          value.append(path).append('\n');
        }
        for (String fileName: groupedByCommonPrefix.get(path)) {
          if (path.length() > 0) {
            value.append("./");
          }
          value.append(fileName).append('\n');
        }
      }

      result.put(CHANGED_FILES_ATTR_PREFIX + parentCommit, value.length() > 0 ? value.substring(0, value.length() - 1) : "");
    }
    return result;
  }


  @Nullable
  VcsChange getVcsChange(String currentVersion, String parentVersion) {
    final String path = getPathString();
    final ChangeType gitChangeType = classifyChange();
    fillPerParentChangedFiles(path);

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


  private void fillPerParentChangedFiles(@NotNull String path) {
    int treeCount = getTreeCount();
    if (!myParentCommits.isEmpty() && myParentCommits.size() == treeCount - 1) {
      for (int i = 1; i < treeCount; i++) {
        if (!idEqual(0, i) || !Objects.equals(getFileMode(0), getFileMode(i))) {
          String parentCommit = myParentCommits.get(i - 1);
          List<String> changedFiles = myPerParentChangedFiles.get(parentCommit);
          if (changedFiles != null) {
            changedFiles.add(path);
          }
        }
      }
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