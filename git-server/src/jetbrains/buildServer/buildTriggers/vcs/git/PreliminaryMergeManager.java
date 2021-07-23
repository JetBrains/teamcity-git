package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.spec.BranchSpecs;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

public class PreliminaryMergeManager implements RepositoryStateListener {
  private final BranchSpecs myBranchSpecs;
  private final InternalGitBranchSupport myBranchSupport;
  private final MergeSupport myMergeSupport;
  private final GitVcsSupport myVcs;
  public static final String PRELIMINARY_MERGE_BRANCH_PREFIX = "premerge";

  public PreliminaryMergeManager(@NotNull final EventDispatcher<RepositoryStateListener> repositoryStateEvents,
                                 @NotNull final BranchSpecs branchSpecs,
                                 @NotNull InternalGitBranchSupport branchSupport,
                                 @NotNull MergeSupport mergeSupport,
                                 @NotNull GitVcsSupport vcs) {
    myBranchSpecs = branchSpecs;
    myBranchSupport = branchSupport;
    myMergeSupport = mergeSupport;
    myVcs = vcs;

    repositoryStateEvents.addListener(this);
  }

  private void printTmp(String toPrint) {
    Loggers.VCS.debug(toPrint);
    System.out.println(toPrint);
  }

  private String branchRevisionsToString(Map<String, String> revs) {
    StringBuilder revisions = new StringBuilder();
    revs.forEach((key, value) -> revisions.append("\n{\n\t").append(key).append(" : ").append(value).append("\n}"));
    return revisions.append("\n").toString();
  }

  @Override
  public void beforeRepositoryStateUpdate(@NotNull VcsRoot root, @NotNull RepositoryState oldState, @NotNull RepositoryState newState) {

  }

  @Override
  public void repositoryStateChanged(@NotNull VcsRoot root, @NotNull RepositoryState oldState, @NotNull RepositoryState newState) {
    printTmp("GitPluginPM: new state: " +
             branchRevisionsToString(oldState.getBranchRevisions()) +
             " > " +
             branchRevisionsToString(newState.getBranchRevisions()));

    PreliminaryMergeBranchesExtractor branchesExecutor = new PreliminaryMergeBranchesExtractor(root, oldState, newState, myBranchSpecs);
    branchesExecutor.extractBranchesFromParams();
    Pair<String, Pair<String, String>> targetBranchState = branchesExecutor.getTargetBranchState();
    HashMap<String, Pair<String, String>> sourceBranchesStates = branchesExecutor.getSourceBranchesStates();
    try {
      preliminaryMerge(root, targetBranchState, sourceBranchesStates);
    } catch (VcsException vcsException) {
      vcsException.printStackTrace();
    }
  }

  private void preliminaryMerge(@NotNull VcsRoot root,
                                @NotNull Pair<String, Pair<String, String>> targetBranchStates,
                                @NotNull HashMap<String, Pair<String, String>> srcBrachesStates) throws VcsException {
    OperationContext context = myVcs.createContext(root, "preliminaryMerge");
    Repository db = context.getRepository();
    Git git = new Git(db);
    GitVcsRoot gitRoot = context.getGitRoot();

    try {
      for (String sourceBranchName : srcBrachesStates.keySet()) {
        String mergeBranchName = myBranchSupport.constructName(sourceBranchName, targetBranchStates.first);
        if (myBranchSupport.branchLastCommit(mergeBranchName, git, db) == null) {
          if (wereTargetOrSourceCreatedOrUpdated(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            myBranchSupport.createBranch(gitRoot, git, db, context, sourceBranchName, mergeBranchName);
            myBranchSupport.merge(root, targetBranchStates.second.second, GitUtils.expandRef(mergeBranchName), myMergeSupport);
          }
        }
        else {
          if (wereTargetAndSourceUpdatedBoth(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            mergeSrcAndTargetToPMBranch(root, targetBranchStates, sourceBranchName, mergeBranchName, git, db);
          }
          else if (isBranchRenewed(srcBrachesStates, sourceBranchName)) {
            myBranchSupport.fetch(mergeBranchName, git, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, targetBranchStates.first)) {
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
            else {
              mergeSrcAndTargetToPMBranch(root, targetBranchStates, sourceBranchName, mergeBranchName, git, db);
            }
          }
          else if (isBranchRenewed(targetBranchStates.second)) {
            myBranchSupport.fetch(mergeBranchName, git, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, sourceBranchName)) {
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
            else {
              mergeSrcAndTargetToPMBranch(root, targetBranchStates, sourceBranchName, mergeBranchName, git, db);
            }
          }
        }
      }
    } catch (VcsException | IOException exception) {
      Loggers.VCS.debug(exception);
    }

  }

  private void mergeSrcAndTargetToPMBranch(@NotNull VcsRoot root,
                                           @NotNull Pair<String, Pair<String, String>> targetBranchStates,
                                           @NotNull String sourceBranchName,
                                           @NotNull String mergeBranchName,
                                           @NotNull Git git,
                                           @NotNull Repository db) throws VcsException {
    myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                          GitUtils.expandRef(mergeBranchName), myMergeSupport);
    myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                          GitUtils.expandRef(mergeBranchName), myMergeSupport);
  }

  private boolean wereTargetAndSourceUpdatedBoth(@NotNull Pair<String, Pair<String, String>> targerBranchStates,
                                                 @NotNull HashMap<String, Pair<String, String>> srcBrachesStates,
                                                 @NotNull String sourceBranchName) {
    return  !wereBothTargerAndSourceNewlyCreated(targerBranchStates, srcBrachesStates, sourceBranchName) &&
            isBranchRenewed(targerBranchStates.second) &&
            isBranchRenewed(srcBrachesStates, sourceBranchName);
  }

  private boolean wereTargetOrSourceCreatedOrUpdated(@NotNull Pair<String, Pair<String, String>> targerBranchStates,
                                                        @NotNull HashMap<String, Pair<String, String>> srcBrachesStates,
                                                        @NotNull String sourceBranchName) {
    return isBranchNewlyCreated(targerBranchStates.second) ||
           isBranchNewlyCreated(srcBrachesStates, sourceBranchName) ||
           isBranchRenewed(targerBranchStates.second) ||
           isBranchRenewed(srcBrachesStates, sourceBranchName);
  }

  private boolean wereBothTargerAndSourceNewlyCreated(@NotNull Pair<String, Pair<String, String>> targerBranchStates,
                                                      @NotNull HashMap<String, Pair<String, String>> srcBrachesStates,
                                                      @NotNull String sourceBranchName) {
    return isBranchNewlyCreated(targerBranchStates.second) &&
           isBranchNewlyCreated(srcBrachesStates, sourceBranchName);
  }

  private boolean isBranchRenewed(Pair<String, String> branchStates) {
    return !branchStates.first.equals(branchStates.second);
  }

  private boolean isBranchRenewed(HashMap<String, Pair<String, String>> states, String branchName) {
    return !states.get(branchName).first.equals(states.get(branchName).second);
  }

  private boolean isBranchNewlyCreated(Pair<String, String> branchStates) {
    return branchStates.first == null && branchStates.second != null;
  }

  private boolean isBranchNewlyCreated(HashMap<String, Pair<String, String>> states, String branchName) {
    return states.get(branchName).first == null && states.get(branchName).second != null;
  }


}
