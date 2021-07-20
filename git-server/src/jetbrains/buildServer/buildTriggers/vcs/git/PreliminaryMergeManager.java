package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import java.util.function.Predicate;
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

    VcsRootParametersExtractor paramsExecutor = new VcsRootParametersExtractor(root);

    //parameter: teamcity.internal.vcs.preliminaryMerge.<VCS root external id>=<source branch pattern>:<target branch name>

    //todo refactor: universal method for getting branches
    Map.Entry<String, String> externalIdOnSourcesTargetBranchesParam = paramsExecutor.getParameterWithExternalId("teamcity.internal.vcs.preliminaryMerge");
    if (externalIdOnSourcesTargetBranchesParam == null) {
      return;
    }

    Map.Entry<String, String> sourcesTargetBranches = parsePreliminaryMergeSourcesTargetBranches(externalIdOnSourcesTargetBranchesParam.getValue());

    if (sourcesTargetBranches == null)
      return;

    String targetBranchName = sourcesTargetBranches.getValue();
    Pair<String, Pair<String, String>> targetBranchState = new Pair<>(targetBranchName,
                                                                       new Pair<>(oldState.getBranchRevisions().get(targetBranchName),
                                                                                  newState.getBranchRevisions().get(targetBranchName)));

    HashMap<String, Pair<String, String>> sourceBranchesStates = createSourceBranchStates(oldState, newState, sourcesTargetBranches.getKey(), targetBranchName);

    //iterate src branches, and create branch for each renewed
    try {
      mergingPrototype(root, targetBranchState, sourceBranchesStates);
    } catch (VcsException vcsException) {
      vcsException.printStackTrace();
    }
  }

  private void mergingPrototype(VcsRoot root, Pair<String, Pair<String, String>> targetBranchStates, HashMap<String, Pair<String, String>> srcBrachesStates) throws VcsException {
    OperationContext context = myVcs.createContext(root, "branchCreation");
    Repository db = context.getRepository();
    Git git = new Git(db);
    GitVcsRoot gitRoot = context.getGitRoot();

    try {
      for (String sourceBranchName : srcBrachesStates.keySet()) {
        String mergeBranchName = myBranchSupport.constructName(sourceBranchName, targetBranchStates.first);
        if (myBranchSupport.branchLastCommit(mergeBranchName, git, db) == null) {
          if (wereTargetOrSourceCreatedOrUpdated(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            System.out.println("shuold create new branch");

            myBranchSupport.createBranchProto(gitRoot, git, db, context, sourceBranchName, targetBranchStates.first);
            myBranchSupport.merge(root, targetBranchStates.second.second, GitUtils.expandRef(mergeBranchName), myMergeSupport);

            System.out.println("merged " + targetBranchStates.second.second + " to " + mergeBranchName);
          }
        }
        else {
          if (wereTargetAndSourceUpdatedBoth(targetBranchStates, srcBrachesStates, sourceBranchName)) {
            System.out.println("both src and target were updated. This case will be done later");
            myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                  GitUtils.expandRef(mergeBranchName), myMergeSupport);
            myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                                  GitUtils.expandRef(mergeBranchName), myMergeSupport);
          }
          else if (isBranchRenewed(srcBrachesStates, sourceBranchName)) { //and target branch has commit, which is parent for PR commit
            myBranchSupport.fetch(mergeBranchName, git, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, targetBranchStates.first)) {

              System.out.println("src branch was renewed case");
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
            else {
              System.out.println("both src and target were updated. This case will be done later #2");
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
          }
          else if (isBranchRenewed(targetBranchStates.second)) { //and src branch has commit, which is parent for PR commit
            myBranchSupport.fetch(mergeBranchName, git, db, gitRoot);
            if (myBranchSupport.isBranchTopCommitInTree(mergeBranchName, git, db, sourceBranchName)) {
              System.out.println("dst branch was renewed case");
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
            else {
              System.out.println("both src and target were updated. This case will be done later #3");
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(sourceBranchName, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
              myBranchSupport.merge(root, Objects.requireNonNull(myBranchSupport.branchLastCommit(targetBranchStates.first, git, db)),
                                    GitUtils.expandRef(mergeBranchName), myMergeSupport);
            }
          }
        }
      }
    } catch (VcsException | IOException exception) {
      Loggers.VCS.debug(exception);
    }

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

  private HashMap<String, Pair<String, String>> createSourceBranchStates(@NotNull RepositoryState oldState, @NotNull RepositoryState newState, @NotNull String sourceBranchFilter,
                                                                         @NotNull String targetBranch) {
    HashMap<String, Pair<String, String>> states = new HashMap<>();

    Set<String> branchesSet = new HashSet<>(oldState.getBranchRevisions().keySet());
    branchesSet.addAll(newState.getBranchRevisions().keySet());

    Predicate<String> filter = new PreliminaryMergeSourceBranchFilter(myBranchSpecs, sourceBranchFilter);
    for (String branch : branchesSet) {
      if (!filter.test(branch) || branch.equals(targetBranch)) {
        continue;
      }

      states.put(branch, new Pair<>(oldState.getBranchRevisions().get(branch), newState.getBranchRevisions().get(branch)));
    }
    return states;
  }

  private Map.Entry<String, String> parsePreliminaryMergeSourcesTargetBranches(String paramValue) {
    //regexes are too slow for this simple task
    for (int i = 1; i < paramValue.length(); ++i) {
      if (paramValue.charAt(i) == ':' && paramValue.charAt(i-1) != '+' && paramValue.charAt(i-1) != '0') {
        return new AbstractMap.SimpleEntry<>(paramValue.substring(0, i), paramValue.substring(i+1));
      }
    }

    return null; //todo exception???
  }
}
