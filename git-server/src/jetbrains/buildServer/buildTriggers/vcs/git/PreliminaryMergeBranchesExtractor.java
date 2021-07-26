package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.*;
import java.util.function.Predicate;
import jetbrains.buildServer.vcs.RepositoryState;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.spec.BranchSpecs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreliminaryMergeBranchesExtractor {
  private final VcsRoot myRoot;
  private final RepositoryState myOldState;
  private final RepositoryState myNewState;
  private final BranchSpecs myBranchSpecs;
  private String targetBranchName;
  private BranchStates targetBranchStates;
  private HashMap<String, BranchStates> sourceBranchesAndStates;

  public PreliminaryMergeBranchesExtractor(@NotNull VcsRoot root,
                                           @NotNull RepositoryState oldState,
                                           @NotNull RepositoryState newState,
                                           @NotNull BranchSpecs branchSpecs) {
    myRoot = root;
    myOldState = oldState;
    myNewState = newState;
    myBranchSpecs = branchSpecs;
  }

  public VcsRoot getRoot() {
    return myRoot;
  }

  public BranchStates getTargetBranchStates() {
    if (targetBranchName == null) {
      throw new IllegalStateException("target branch states are not establised. Call extractBranchesFromParams()");
    }
    return targetBranchStates;
  }

  public String getTargetBranchName() {
    if (targetBranchName == null) {
      throw new IllegalStateException("target branch name is not establised. Call extractBranchesFromParams()");
    }
    return targetBranchName;
  }

  public HashMap<String, BranchStates> getSourceBranchesAndStates() {
    if (sourceBranchesAndStates == null) {
      throw new IllegalStateException("source branches are not established. Call extractBranchesFromParams()");
    }
    return sourceBranchesAndStates;
  }

  @Nullable
  public Map.Entry<String, String> getParameterWithExternalId(String paramBeforeExternalId) {
    VcsRoot currentRoot = myRoot;

    while (currentRoot instanceof VcsRootInstance) {
      currentRoot = ((VcsRootInstance) currentRoot).getParent();
    }

    if (currentRoot instanceof SVcsRootEx) {
      SVcsRootEx currentRootEx = (SVcsRootEx) currentRoot;
      String externalId = currentRootEx.getExternalId();
      String parameter = currentRootEx.getProject().getParameterValue(paramBeforeExternalId + "." + externalId);

      return new java.util.AbstractMap.SimpleEntry<>(externalId, parameter);
    }
    else {
      PreliminaryMergeManager.printToLogs("root extraction error");
      return null;
    }
  }

  public void extractBranchesFromParams() {
    //parameter: teamcity.internal.vcs.preliminaryMerge.<VCS root external id>=<source branch pattern>:<target branch name>
    Map.Entry<String, String> PMParam = getParameterWithExternalId("teamcity.internal.vcs.preliminaryMerge");
    if (PMParam == null) {
      return;
    }
    Map.Entry<String, String> sourcesTargetBranches = parsePMParam(PMParam.getValue());

    if (sourcesTargetBranches == null) {
      return;
    }

    targetBranchName = sourcesTargetBranches.getValue();

    String targetBranchNewState = myNewState.getBranchRevisions().get(targetBranchName);
    if (targetBranchNewState == null) {
      PreliminaryMergeManager.printToLogs("target branch is not existent");
      return;
    }

    targetBranchStates = new BranchStates(myOldState.getBranchRevisions().get(targetBranchName), myNewState.getBranchRevisions().get(targetBranchName));
    sourceBranchesAndStates = createSourceBranchStates(myOldState, myNewState, sourcesTargetBranches.getKey(), targetBranchName);
  }

  private Map.Entry<String, String> parsePMParam(String paramValue) {
    //regexes are too slow for this simple task
    for (int i = 1; i < paramValue.length(); ++i) {
      if (paramValue.charAt(i) == ':' && paramValue.charAt(i-1) != '+' && paramValue.charAt(i-1) != '0') {
        return new AbstractMap.SimpleEntry<>(paramValue.substring(0, i), paramValue.substring(i + 1));
      }
    }

    return null;
  }

  private HashMap<String, BranchStates> createSourceBranchStates(@NotNull RepositoryState oldState,
                                                                 @NotNull RepositoryState newState,
                                                                 @NotNull String sourceBranchFilter,
                                                                 @NotNull String targetBranch) {
    HashMap<String, BranchStates> states = new HashMap<>();
    Set<String> branchesSet = new HashSet<>(oldState.getBranchRevisions().keySet());
    branchesSet.addAll(newState.getBranchRevisions().keySet());

    Predicate<String> filter = new PreliminaryMergeSourceBranchFilter(myBranchSpecs, sourceBranchFilter);
    for (String branch : branchesSet) {
      if (!filter.test(branch) || branch.equals(targetBranch)) {
        continue;
      }

      states.put(branch, new BranchStates(oldState.getBranchRevisions().get(branch), newState.getBranchRevisions().get(branch)));
    }
    return states;
  }
}
