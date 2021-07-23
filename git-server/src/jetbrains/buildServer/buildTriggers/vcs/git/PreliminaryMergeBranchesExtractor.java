package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
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
  private final VcsRoot root;
  private final RepositoryState oldState;
  private final RepositoryState newState;
  private final BranchSpecs branchSpecs;
  private Pair<String, Pair<String, String>> targetBranchState;
  private HashMap<String, Pair<String, String>> sourceBranchesStates;

  public PreliminaryMergeBranchesExtractor(@NotNull VcsRoot root,
                                           @NotNull RepositoryState oldState,
                                           @NotNull RepositoryState newState,
                                           @NotNull BranchSpecs branchSpecs) {
    this.root = root;
    this.oldState = oldState;
    this.newState = newState;
    this.branchSpecs = branchSpecs;
  }

  public VcsRoot getRoot() {
    return root;
  }

  public Pair<String, Pair<String, String>> getTargetBranchState() {
    return targetBranchState;
  }

  public HashMap<String, Pair<String, String>> getSourceBranchesStates() {
    return sourceBranchesStates;
  }

  @Nullable
  public Map.Entry<String, String> getParameterWithExternalId(String paramBeforeExternalId) {
    VcsRoot currentRoot = root;

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
      throw new AssertionError("Root error");
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

    String targetBranchName = sourcesTargetBranches.getValue();
    targetBranchState = new Pair<>(targetBranchName,
                                   new Pair<>(oldState.getBranchRevisions().get(targetBranchName),
                                                               newState.getBranchRevisions().get(targetBranchName)));

    sourceBranchesStates = createSourceBranchStates(oldState, newState, sourcesTargetBranches.getKey(), targetBranchName);
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

  private HashMap<String, Pair<String, String>> createSourceBranchStates(@NotNull RepositoryState oldState, @NotNull RepositoryState newState, @NotNull String sourceBranchFilter,
                                                                         @NotNull String targetBranch) {
    HashMap<String, Pair<String, String>> states = new HashMap<>();
    Set<String> branchesSet = new HashSet<>(oldState.getBranchRevisions().keySet());
    branchesSet.addAll(newState.getBranchRevisions().keySet());

    Predicate<String> filter = new PreliminaryMergeSourceBranchFilter(branchSpecs, sourceBranchFilter);
    for (String branch : branchesSet) {
      if (!filter.test(branch) || branch.equals(targetBranch)) {
        continue;
      }

      states.put(branch, new Pair<>(oldState.getBranchRevisions().get(branch), newState.getBranchRevisions().get(branch)));
    }
    return states;
  }
}
