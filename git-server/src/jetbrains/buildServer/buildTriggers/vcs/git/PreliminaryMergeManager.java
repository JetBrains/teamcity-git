package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.util.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import java.util.function.Predicate;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.spec.BranchSpecs;
import org.jetbrains.annotations.NotNull;

public class PreliminaryMergeManager implements RepositoryStateListener {
  private final BranchSpecs myBranchSpecs;
  private final InternalGitBranchSupport myBranchSupport;
  private final MergeSupport myMergeSupport;

  public PreliminaryMergeManager(@NotNull final EventDispatcher<RepositoryStateListener> repositoryStateEvents,
                                 @NotNull final BranchSpecs branchSpecs,
                                 @NotNull InternalGitBranchSupport branchSupport,
                                 @NotNull MergeSupport mergeSupport) {
    myBranchSpecs = branchSpecs;
    myBranchSupport = branchSupport;
    myMergeSupport = mergeSupport;

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

    System.out.println("source pattern: " + sourcesTargetBranches.getKey());
    System.out.println("target branch: " + sourcesTargetBranches.getValue());

    String targetBranchName = sourcesTargetBranches.getValue();
    Pair<String, Pair<String, String>> targetBranchState = new Pair<>(targetBranchName,
                                                                       new Pair<>(oldState.getBranchRevisions().get(targetBranchName),
                                                                                  newState.getBranchRevisions().get(targetBranchName)));

    System.out.println("targetBranchStates: " + targetBranchState);

    HashMap<String, Pair<String, String>> sourceBranchesStates = createSourceBranchStates(oldState, newState, sourcesTargetBranches.getKey(), targetBranchName);

    System.out.println("States: " + sourceBranchesStates);

    //iterate src branches, and create branch for each renewed

    mergingPrototype(root, targetBranchState, sourceBranchesStates);
  }

  private void mergingPrototype(VcsRoot root, Pair<String, Pair<String, String>> targerBranchStates, HashMap<String, Pair<String, String>> srcBrachesStates) {
    for (String sourceBranchName : srcBrachesStates.keySet()) {
      if (isBranchRenewed(srcBrachesStates, sourceBranchName)) {
        System.out.println("this branch is renewed: " + sourceBranchName);

        try {
          String mergeBranchName = "refs/heads/" + myBranchSupport.createBranchProto(root, sourceBranchName);
          myBranchSupport.merge(root, targerBranchStates.second.second, mergeBranchName, myMergeSupport);
          System.out.println("merged " + targerBranchStates.second.second + " to " + mergeBranchName);
        } catch (VcsException vcsException) {
          //todo logging
          vcsException.printStackTrace();
        }
      }
    }
  }

  private boolean isBranchRenewed(Pair<String, String> branchStates) {
    return !branchStates.first.equals(branchStates.second);
  }

  private boolean isBranchRenewed(HashMap<String, Pair<String, String>> states, String branchName) {
    return !states.get(branchName).first.equals(states.get(branchName).second);
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
