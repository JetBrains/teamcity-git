package jetbrains.buildServer.buildTriggers.vcs.git;

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

  public static void printToLogs(String toPrint) {
    Loggers.VCS.debug("[Preliminary_Merge_feature]: " + toPrint);
  }

  @Override
  public void beforeRepositoryStateUpdate(@NotNull VcsRoot root, @NotNull RepositoryState oldState, @NotNull RepositoryState newState) {

  }

  @Override
  public void repositoryStateChanged(@NotNull VcsRoot root, @NotNull RepositoryState oldState, @NotNull RepositoryState newState) {
    printToLogs("Repository update event");
    PreliminaryMergeBranchesExtractor branchesExecutor = new PreliminaryMergeBranchesExtractor(root, oldState, newState, myBranchSpecs);
    branchesExecutor.extractBranchesFromParams();
    String targetBranchName = branchesExecutor.getTargetBranchName();
    BranchStates targetBranchStates = branchesExecutor.getTargetBranchStates();
    HashMap<String, BranchStates> sourceBranchesStates = branchesExecutor.getSourceBranchesAndStates();
    try {
      preliminaryMerge(root, targetBranchName, targetBranchStates, sourceBranchesStates);
    } catch (VcsException vcsException) {
      Loggers.VCS.warnAndDebugDetails("repository state change error", vcsException);
    }
  }

  private void preliminaryMerge(@NotNull VcsRoot root,
                                @NotNull String targetBranchName,
                                @NotNull BranchStates targetBranchStates,
                                @NotNull HashMap<String, BranchStates> srcBrachesStates) throws VcsException {
    OperationContext context = myVcs.createContext(root, "preliminaryMerge");
    Repository db = context.getRepository();
    Git git = new Git(db);
    GitVcsRoot gitRoot = context.getGitRoot();

    try {
      //myBranchSupport.deleteBranch(gitRoot, git, db, context, "del_1"); //works

      for (String sourceBranchName : srcBrachesStates.keySet()) {
        String mergeBranchName = myBranchSupport.constructName(sourceBranchName, targetBranchName);
        if (myBranchSupport.branchLastCommit(mergeBranchName, git, db) == null) {
          if (wereTargetOrSourceCreatedOrUpdated(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            printToLogs(sourceBranchName + " > " + targetBranchName + " (should create new PM branch)");
            myBranchSupport.createBranch(gitRoot, git, db, context, sourceBranchName, mergeBranchName);
            myMergeSupport.merge(root, targetBranchStates.getNewState(), GitUtils.expandRef(mergeBranchName), "preliminary merge commit", new MergeOptions());
          }
        }
        else {
          if (wereTargetAndSourceUpdatedBoth(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            printToLogs(sourceBranchName + " > " + targetBranchName + " (both were updated)");
            mergeSrcAndTargetToPMBranch(root, targetBranchName, sourceBranchName, mergeBranchName, git, db);
          }
          else if (srcBrachesStates.get(sourceBranchName).isBranchRenewed()) {
            myBranchSupport.fetch(mergeBranchName, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, targetBranchName)) {
              printToLogs(sourceBranchName + " > " + targetBranchName + " (merge src to PM branch)");
              myMergeSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), "preliminary merge commit", new MergeOptions());
            }
            else {
              printToLogs(sourceBranchName + " > " + targetBranchName + " (merge src to PM branch (+target due to lost))");
              mergeSrcAndTargetToPMBranch(root, targetBranchName, sourceBranchName, mergeBranchName, git, db);
            }
          }
          else if (targetBranchStates.isBranchRenewed()) {
            myBranchSupport.fetch(mergeBranchName, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, sourceBranchName)) {
              printToLogs(sourceBranchName + " > " + targetBranchName + " (merge dst to PM branch)");
              myMergeSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), "preliminary merge commit", new MergeOptions());
            }
            else {
              printToLogs(sourceBranchName + " > " + targetBranchName + " (merge dst to PM branch (+src due to lost))");
              mergeSrcAndTargetToPMBranch(root, targetBranchName, sourceBranchName, mergeBranchName, git, db);
            }
          }
        }
        printToLogs("PM handling is successful");
      }
    } catch (VcsException | IOException exception) {
      Loggers.VCS.warnAndDebugDetails("preliminary merge error", exception);
    }

  }

  private void mergeSrcAndTargetToPMBranch(@NotNull VcsRoot root,
                                           @NotNull String targetBranchName,
                                           @NotNull String sourceBranchName,
                                           @NotNull String mergeBranchName,
                                           @NotNull Git git,
                                           @NotNull Repository db) throws VcsException {
    myMergeSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                          GitUtils.expandRef(mergeBranchName), "preliminary merge commit", new MergeOptions());
    myMergeSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchName, git, db)),
                          GitUtils.expandRef(mergeBranchName), "preliminary merge commit", new MergeOptions());
  }

  private boolean wereTargetAndSourceUpdatedBoth(@NotNull BranchStates targerBranchStates,
                                                 @NotNull HashMap<String, BranchStates> srcBrachesStates,
                                                 @NotNull String sourceBranchName) {
    return  !wereBothTargerAndSourceNewlyCreated(targerBranchStates, srcBrachesStates, sourceBranchName) &&
            targerBranchStates.isBranchRenewed() &&
            srcBrachesStates.get(sourceBranchName).isBranchRenewed();
  }

  private boolean wereTargetOrSourceCreatedOrUpdated(@NotNull BranchStates targerBranchStates,
                                                     @NotNull HashMap<String, BranchStates> srcBrachesStates,
                                                     @NotNull String sourceBranchName) {
    return targerBranchStates.isBranchNewlyCreated() ||
           srcBrachesStates.get(sourceBranchName).isBranchNewlyCreated() ||
           targerBranchStates.isBranchRenewed() ||
           srcBrachesStates.get(sourceBranchName).isBranchRenewed();
  }

  private boolean wereBothTargerAndSourceNewlyCreated(@NotNull BranchStates targerBranchStates,
                                                      @NotNull HashMap<String, BranchStates> srcBrachesStates,
                                                      @NotNull String sourceBranchName) {
    return targerBranchStates.isBranchNewlyCreated() &&
           srcBrachesStates.get(sourceBranchName).isBranchNewlyCreated();
  }
}
