

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.IgnoreSubmoduleErrorsTreeFilter;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.MissingSubmoduleCommitInfo;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.DBVcsModification;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.SubmoduleAwareTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
class ModificationDataRevWalk extends LimitingRevWalk {

  private static final Logger LOG = Logger.getInstance(ModificationDataRevWalk.class.getName());

  private final int mySearchDepth;

  ModificationDataRevWalk(@NotNull ServerPluginConfig config, @NotNull OperationContext context) throws VcsException {
    super(config, context);
    mySearchDepth = config.getFixedSubmoduleCommitSearchDepth();
  }

  @NotNull
  public ModificationData createModificationData() throws IOException {
    checkCurrentCommit();

    final String commitId = getCurrentCommit().getId().name();
    String message = GitServerUtil.getFullMessage(getCurrentCommit());
    final PersonIdent authorIdent = GitServerUtil.getAuthorIdent(getCurrentCommit());
    final Date authorDate = authorIdent.getWhen();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Collecting changes in commit " + commitId + ":" + message + " (" + authorDate + ") for " + getGitRoot().debugInfo());
    }

    final String parentVersion = getFirstParentVersion(getCurrentCommit());
    final CommitChangesBuilder builder = new CommitChangesBuilder(getCurrentCommit(), commitId, parentVersion);
    builder.collectCommitChanges();
    final List<VcsChange> changes = builder.getChanges();

    final String author = GitServerUtil.getUser(getGitRoot(), authorIdent);
    final ModificationData result = new ModificationData(
      authorDate,
      changes,
      message,
      author,
      getGitRoot().getOriginalRoot(),
      commitId,
      commitId);

    Map<String, String> attributes = builder.getAttributes();
    if (!attributes.isEmpty())
      result.setAttributes(attributes);

    final PersonIdent commiterIdent = GitServerUtil.getCommitterIdent(getCurrentCommit());
    final String commiter = GitServerUtil.getUser(getGitRoot(), commiterIdent);
    final Date commitDate = commiterIdent.getWhen();
    if (!Objects.equals(authorDate, commitDate)) {
      result.setAttribute(DBVcsModification.TEAMCITY_COMMIT_TIME, Long.toString(commitDate.getTime()));
    }
    if (!Objects.equals(author, commiter)) {
      result.setAttribute(DBVcsModification.TEAMCITY_COMMIT_USER, commiter);
    }

    if (getCurrentCommit().getParentCount() > 0) {
      for (RevCommit parent : getCurrentCommit().getParents()) {
        parseBody(parent);
        result.addParentRevision(parent.getId().name());
      }
    } else {
      result.addParentRevision(ObjectId.zeroId().name());
    }
    return result;
  }

  private boolean shouldIgnoreSubmodulesErrors() {
    return getVisitedCommitsNum() > 1;//ignore submodule errors for all commits excluding the first one
  }

  @NotNull
  private String getFirstParentVersion(@NotNull final RevCommit commit) throws IOException {
    final RevCommit[] parents = commit.getParents();
    if (parents.length == 0) {
      return ObjectId.zeroId().name();
    } else {
      RevCommit parent = parents[0];
      parseBody(parent);
      return parent.getId().name();
    }
  }

  private class CommitChangesBuilder {
    private final RevCommit commit;
    private final String currentVersion;
    private final String parentVersion;
    private final List<VcsChange> changes = new ArrayList<VcsChange>();
    private final Map<String, String> myAttributes = new HashMap<>();
    private final String repositoryDebugInfo = getGitRoot().debugInfo();
    private final IgnoreSubmoduleErrorsTreeFilter filter = new IgnoreSubmoduleErrorsTreeFilter(getGitRoot());
    private final Map<String, String> commitsWithFix = new HashMap<String, String>();
    @Nullable
    private final MissingSubmoduleCommitInfo missingSubmoduleCommitInfo;

    /**
     * @param commit current commit
     * @param currentVersion teamcity version of current commit (sha@time)
     * @param parentVersion parent version to use in VcsChange objects
     */
    public CommitChangesBuilder(@NotNull final RevCommit commit,
                                @NotNull final String currentVersion,
                                @NotNull final String parentVersion) {
      this.commit = commit;
      this.currentVersion = currentVersion;
      this.parentVersion = parentVersion;
      // TODO currently we don't use missingSubmoduleCommitInfo, but later it should be stored in attributes(the format is to be defined) and display the information in the ui. See TW-91296
      missingSubmoduleCommitInfo = TeamCityProperties.getBoolean(Constants.IGNORE_SUBMODULE_ERRORS) ? new MissingSubmoduleCommitInfo() : null;
    }

    @NotNull
    public List<VcsChange> getChanges() {
      return changes;
    }

    @NotNull
    public Map<String, String> getAttributes() {
      return myAttributes;
    }

    @Nullable
    public MissingSubmoduleCommitInfo getMissingSubmoduleCommitInfo() {
      return missingSubmoduleCommitInfo;
    }

    /**
     * collect changes for the commit
     */
    public void collectCommitChanges() throws IOException {
      try (VcsChangeTreeWalk tw = new VcsChangeTreeWalk(getRepository(), repositoryDebugInfo, getConfig().verboseTreeWalkLog())) {
        tw.setFilter(filter);
        tw.setRecursive(true);
        getContext().addTree(getGitRoot(), tw, getRepository(), commit, missingSubmoduleCommitInfo, shouldIgnoreSubmodulesErrors());
        RevCommit[] parents = commit.getParents();
        boolean reportPerParentChangedFiles =
          getConfig().reportPerParentChangedFiles() && parents.length > 1; // report only for merge commits
        for (RevCommit parentCommit : parents) {
          getContext().addTree(getGitRoot(), tw, getRepository(), parentCommit, missingSubmoduleCommitInfo, true);
          if (reportPerParentChangedFiles) {
            tw.reportChangedFilesForParentCommit(parentCommit);
          }
        }

        new VcsChangesTreeWalker(tw, missingSubmoduleCommitInfo).walk();

        if (reportPerParentChangedFiles) {
          Map<String, String> changedFilesAttributes = tw.buildChangedFilesAttributes();
          if (!changedFilesAttributes.isEmpty()) {
            myAttributes.putAll(changedFilesAttributes);
          }
        }
      }
    }

    private class VcsChangesTreeWalker {
      private final VcsChangeTreeWalk tw;
      @Nullable
      private final MissingSubmoduleCommitInfo myMissingSubmoduleCommitInfo;

      private VcsChangesTreeWalker(@NotNull final VcsChangeTreeWalk tw, @Nullable MissingSubmoduleCommitInfo missingSubmoduleCommitInfo) {
        this.tw = tw;
        myMissingSubmoduleCommitInfo = missingSubmoduleCommitInfo;
      }

      private void walk() throws IOException {
        while (tw.next()) {
          final String path = tw.getPathString();

          processChange(path);
        }
      }

      private void processChange(@NotNull final String path) throws IOException {
        if (!getGitRoot().isCheckoutSubmodules()) {
          addVcsChange();
          return;
        }

        if (filter.isBrokenSubmoduleEntry(path)) {
          final RevCommit commitWithFix = getPreviousCommitWithFixedSubmodule(commit, path, myMissingSubmoduleCommitInfo);
          commitsWithFix.put(path, commitWithFix == null ? null : commitWithFix.getId().name());
          if (commitWithFix != null) {
            subWalk(path, commitWithFix);
            return;
          }
        }

        if (filter.isChildOfBrokenSubmoduleEntry(path)) {
          final String brokenSubmodulePath = filter.getSubmodulePathForChildPath(path);
          final String commitWithFix = commitsWithFix.get(brokenSubmodulePath);
          if (commitWithFix != null) {
            return;
          }
        }

        addVcsChange();
      }

      private void subWalk(@NotNull final String path, @NotNull final RevCommit commitWithFix) throws IOException {
        try (VcsChangeTreeWalk tw2 = new VcsChangeTreeWalk(getRepository(), repositoryDebugInfo, getConfig().verboseTreeWalkLog())) {
          tw2.setFilter(TreeFilter.ANY_DIFF);
          tw2.setRecursive(true);
          getContext().addTree(getGitRoot(), tw2, getRepository(), commit, myMissingSubmoduleCommitInfo, true);
          getContext().addTree(getGitRoot(), tw2, getRepository(), commitWithFix, myMissingSubmoduleCommitInfo, true);
          while (tw2.next()) {
            if (tw2.getPathString().startsWith(path + "/")) {
              addVcsChange(currentVersion, commitWithFix.getId().name(), tw2);
            }
          }
        }
      }

      private void addVcsChange() {
        addVcsChange(currentVersion, parentVersion, tw);
      }

      private void addVcsChange(@NotNull final String currentVersion,
                                @NotNull final String parentVersion,
                                @NotNull final VcsChangeTreeWalk tw) {
        final VcsChange change = tw.getVcsChange(currentVersion, parentVersion);
        if (change != null) changes.add(change);
      }
    }

    @Nullable
    private RevCommit getPreviousCommitWithFixedSubmodule(@NotNull final RevCommit fromCommit,
                                                          @NotNull final String submodulePath,
                                                          @Nullable MissingSubmoduleCommitInfo missingSubmoduleCommitInfo)
      throws IOException {
      if (mySearchDepth == 0)
        return null;

      try (RevWalk revWalk = new RevWalk(getRepository())) {
        final RevCommit fromRev = revWalk.parseCommit(fromCommit.getId());
        revWalk.markStart(fromRev);
        revWalk.sort(RevSort.TOPO);

        RevCommit result = null;
        RevCommit prevRev;
        revWalk.next();
        int depth = 0;
        while (result == null && depth < mySearchDepth && (prevRev = revWalk.next()) != null) {
          depth++;
          try (TreeWalk prevTreeWalk = new TreeWalk(getRepository())) {
            prevTreeWalk.setFilter(TreeFilter.ALL);
            prevTreeWalk.setRecursive(true);
            getContext().addTree(getGitRoot(), prevTreeWalk, getRepository(), prevRev, missingSubmoduleCommitInfo, true, false, null);
            while (prevTreeWalk.next()) {
              String path = prevTreeWalk.getPathString();
              if (path.startsWith(submodulePath + "/")) {
                final SubmoduleAwareTreeIterator iter = prevTreeWalk.getTree(0, SubmoduleAwareTreeIterator.class);
                if (iter != null) {
                  final SubmoduleAwareTreeIterator parentIter = iter.getParent();
                  if (!iter.isSubmoduleError() && parentIter != null && parentIter.isOnSubmodule()) {
                    result = prevRev;
                    break;
                  }
                }
              }
            }
          }
        }
        return result;
      }
    }
  }
}