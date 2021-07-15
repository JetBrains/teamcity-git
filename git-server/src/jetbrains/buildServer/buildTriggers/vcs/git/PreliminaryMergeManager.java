package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.Hash;
import jetbrains.buildServer.vcs.RepositoryState;
import jetbrains.buildServer.vcs.RepositoryStateListener;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

public class PreliminaryMergeManager implements RepositoryStateListener {

  public PreliminaryMergeManager(@NotNull final EventDispatcher<RepositoryStateListener> repositoryStateEvents) {
    printTmp("GitPluginPM init");
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
    Map.Entry<String, String> externalIdOnSourcesTargetBranchesParam = paramsExecutor.getParameterWithExternalId("teamcity.internal.vcs.preliminaryMerge");
    if (externalIdOnSourcesTargetBranchesParam == null) {
      return;
    }

    Map.Entry<String, String> sourcesTargetBranches = parsePreliminaryMergeSourcesTargetBranches(externalIdOnSourcesTargetBranchesParam.getValue());

    System.out.println("source pattern: " + sourcesTargetBranches.getKey());
    System.out.println("target branch: " + sourcesTargetBranches.getValue());

    String targetBranchName = sourcesTargetBranches.getValue();
    Pair<String, Pair<String, String>> targetBranchStates = new Pair<>(targetBranchName,
                                                                       new Pair<>(oldState.getBranchRevisions().get(targetBranchName),
                                                                                  newState.getBranchRevisions().get(targetBranchName)));

    System.out.println("targetBranchStates: " + targetBranchStates);

    HashMap<String, Pair<String, String>> sourceBranchesStates = createSourceBranchStates(oldState, newState);

    System.out.println("States: " + sourceBranchesStates);

    //next step: filter src branches



  }

  private HashMap<String, Pair<String, String>> createSourceBranchStates(@NotNull RepositoryState oldState, @NotNull RepositoryState newState) {
    HashMap<String, Pair<String, String>> states = new HashMap<>();

    Set<Map.Entry<String, String>> branchesSet = new HashSet<>(oldState.getBranchRevisions().entrySet());
    branchesSet.addAll(newState.getBranchRevisions().entrySet());

    for (Map.Entry<String, String> rev : branchesSet) {
      states.put(rev.getKey(), new Pair<>(oldState.getBranchRevisions().get(rev.getKey()), newState.getBranchRevisions().get(rev.getKey())));
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
