package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCollectChangesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.LogUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsChangeTreeWalk;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.RepositoryManager;
import jetbrains.buildServer.buildTriggers.vcs.git.SGitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.*;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.DBVcsModification;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitProxyChangesCollector {

  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());

  private final GitApiClientFactory myGitApiClientFactory;
  private final Gson myGson;
  private final ParameterFactory myParameterFactory;
  private final RepositoryManager myRepositoryManager;

  public GitProxyChangesCollector(@NotNull ParameterFactory parameterFactory, @NotNull GitApiClientFactory gitApiClientFactory, @NotNull RepositoryManager repositoryManager) {
    myGitApiClientFactory = gitApiClientFactory;
    myRepositoryManager = repositoryManager;
    myParameterFactory = parameterFactory;
    myGson = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
      @Override
      public boolean shouldSkipField(FieldAttributes f) {
        return f.getName().toLowerCase().contains("root");
      }

      @Override
      public boolean shouldSkipClass(Class<?> clazz) {
        return false;
      }
    }).create();
  }

  private static final String GIT_PROXY_URL_PROPERTY = "teamcity.internal.git.gitProxy.url";
  private static final String GIT_PROXY_AUTH_PROPERTY = "teamcity.internal.git.gitProxy.auth";

  private static final String GIT_PROXY_REQUEST_TIMEOUT_INTERNAL_PROPERTY = "teamcity.git.gitProxy.requestTimeoutSeconds";
  private static final int GIT_PROXY_REQUEST_TIMEOUT_DEFAULT = 20;
  private static final String GIT_PROXY_CONNECT_TIMEOUT_INTERNAL_PROPERTY = "teamcity.git.gitProxy.connectTimeoutMs";
  private static final int GIT_PROXY_CONNECT_TIMEOUT_DEFAULT = 3000;
  private static final String GIT_PROXY_CONNECT_RETRY_CNT_INTERNAL_PROPERTY = "teamcity.git.gitProxy.connectTimeout.retryCount";
  private static final int GIT_PROXY_CONNECT_RETRY_CNT_DEFAULT = 1;

  private static final String COMPARE_MODIFICATIONS_WITHOUT_ORDER_LOGGING_PROPERTY = "teamcity.git.gitProxy.logging.unorderedModificationComparison";


  @Nullable
  public GitProxySettings getGitProxyInfo(@NotNull VcsRoot root, @Nullable SProject project, @NotNull String suffix) {
    try {
      if (project == null) {
        return null;
      }

      String vcsUrl = root.getProperty(Constants.FETCH_URL);
      // for now git proxy only works with jetbrains.team repositories
      if (vcsUrl == null || !vcsUrl.contains("jetbrains.team")) {
        return null;
      }

      Parameter urlParam = project.getParameter(GIT_PROXY_URL_PROPERTY + suffix);
      Parameter authParam = project.getParameter(GIT_PROXY_AUTH_PROPERTY + suffix);
      int requestTimeout = TeamCityProperties.getInteger(GIT_PROXY_REQUEST_TIMEOUT_INTERNAL_PROPERTY, GIT_PROXY_REQUEST_TIMEOUT_DEFAULT) * 1000;
      int connectTimeout = TeamCityProperties.getInteger(GIT_PROXY_CONNECT_TIMEOUT_INTERNAL_PROPERTY, GIT_PROXY_CONNECT_TIMEOUT_DEFAULT);
      int connectRetryCnt = TeamCityProperties.getInteger(GIT_PROXY_CONNECT_RETRY_CNT_INTERNAL_PROPERTY, GIT_PROXY_CONNECT_RETRY_CNT_DEFAULT);

      if (urlParam != null && authParam != null) {
        return new GitProxySettings(urlParam.getValue(), myParameterFactory.getRawValue(authParam), requestTimeout, connectTimeout, connectRetryCnt);
      }
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Failed to determine if git proxy should be used", e);
    }
    return null;
  }

  @NotNull
  public List<ModificationData> collectChangesGitProxy(@NotNull VcsRoot root,
                                                        @NotNull RepositoryStateData fromState,
                                                        @NotNull RepositoryStateData toState,
                                                        @NotNull GitProxySettings proxyCredentials) throws VcsException {
    GitVcsRoot gitRoot = new SGitVcsRoot(myRepositoryManager, root, new URIishHelperImpl(), null);

    String url = root.getProperty(Constants.FETCH_URL);
    if (url == null) {
      return Collections.emptyList();
    }

    // try to parse project name and repository name from url. For now git proxy only works with jetbrains.team repositories
    List<String> path = ServerURIParser.createServerURI(url).getPathFragments();
    if (path.size() == 0) {
      return Collections.emptyList();
    }
    String repoName = path.get(path.size() - 1);
    if (repoName.endsWith(".git")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }
    String fullRepoName;
    if (path.size() == 1) {
      fullRepoName = repoName;
    } else {
      fullRepoName = String.format("%s/%s", path.get(path.size() - 2), repoName);
    }
    GitRepoApi client = myGitApiClientFactory.createRepoApi(proxyCredentials, null, fullRepoName);

    List<String> commitPatterns = new ArrayList<>();
    for (Map.Entry<String, String> entry: fromState.getBranchRevisions().entrySet()) {
      commitPatterns.add("^" + entry.getValue());
    }
    for (Map.Entry<String, String> entry: toState.getBranchRevisions().entrySet()) {
      String branch = entry.getKey();
      String branchRevision = entry.getValue();
      String fromStateRev = fromState.getBranchRevisions().get(branch);
      if (fromStateRev == null || !branchRevision.equals(fromStateRev)) {
        commitPatterns.add(branchRevision);
      }
    }

    CommitList commitList;
    try {
      commitList = client.listCommits(Collections.singletonList(new Pair<>("id-range", commitPatterns)), 0, Integer.MAX_VALUE, false, true);
    } catch (Exception e) {
      throw new VcsException("Failed to collect commits from git proxy for collectChanges operation", e);
    }
    List<String> commitIds = commitList.commits.stream().map(commit -> commit.id).collect(Collectors.toList());
    Map<String, CommitInfo> commitInfoMap = commitList.commits.stream().collect(Collectors.toMap(commit -> commit.id, commit -> commit.info));
    List<CommitChange> changes;
    try {
      changes = client.listChanges(commitIds, false, false, false, false, Integer.MAX_VALUE);
    } catch (Exception e) {
      throw new VcsException("Failed to collect changes from git proxy for collectChanges operation", e);
    }

    List<ModificationData> result = new ArrayList<>();
    int i = 0;
    while (i < changes.size()) {
      CommitChange change = changes.get(i);
      CommitInfo info = commitInfoMap.get(changes.get(i).revision);
      i++;
      List<CommitChange> mergeEdgeChanges = null;
      // find diff for other edges of merge commit, when inferMergeCommitChanges was set to false separate commit changes are returned for each edge of the merge commit
      while (i < changes.size() && changes.get(i).revision.equals(info.id)) {
        if (mergeEdgeChanges == null) {
          mergeEdgeChanges = new ArrayList<>();
        }
        mergeEdgeChanges.add(changes.get(i));
        i++;
      }
      if (info == null) {
        LOG.error("There is no commit info for returned revision " + change.revision);
        continue;
      }
      result.add(createModificationDataGitProxy(info, change, gitRoot, root, mergeEdgeChanges));
    }
    return result;
  }

  public void logAnyDifferences(@NotNull List<ModificationData> jgitData, @NotNull List<ModificationData> gitProxyData, @NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState, @NotNull VcsRoot root) {
    if (jgitData.size() != gitProxyData.size()) {
      LOG.info("GitProxy difference was found for VCS root(" + LogUtil.describe(root) + "). Different length. jgit:{" + myGson.toJson(getCommitOnlyModificationDataList(jgitData)) + "}, gitProxy:{" + myGson.toJson(getCommitOnlyModificationDataList(gitProxyData)) + "}," + getStateDiff(fromState, toState));
      return;
    }

    if (TeamCityProperties.getBoolean(COMPARE_MODIFICATIONS_WITHOUT_ORDER_LOGGING_PROPERTY) && notEqualModificationListsById(jgitData, gitProxyData)) {
      jgitData = new ArrayList<>(jgitData);
      gitProxyData = new ArrayList<>(gitProxyData);
      Collections.sort(jgitData, (a, b) -> a.getVersion().compareTo(b.getVersion()));
      Collections.sort(gitProxyData, (a, b) -> a.getVersion().compareTo(b.getVersion()));
    }

    List<ModificationData> diff = new ArrayList<>();
    List<Integer> differentPostions = new ArrayList<>();
    for (int i = 0; i < jgitData.size(); i++) {
      ModDataComparisonResult cmpRes = compareModifications(jgitData.get(i), gitProxyData.get(i));
      if (cmpRes == ModDataComparisonResult.NOT_EQUAL_VCS_CHANGES) {
        diff.add(jgitData.get(i));
        diff.add(gitProxyData.get(i));
        differentPostions.add(i);
      }
      else if (cmpRes == ModDataComparisonResult.NOT_EQUAL_ATTRIBUTES) {
        diff.add(getDataWithoutFileChanges(jgitData.get(i), false, true));
        diff.add(getDataWithoutFileChanges(gitProxyData.get(i), false, true));
        differentPostions.add(i);
      }
      else if (cmpRes == ModDataComparisonResult.NOT_EQUAL_OTHER) {
        diff.add(getDataWithoutFileChanges(jgitData.get(i), false, false));
        diff.add(getDataWithoutFileChanges(gitProxyData.get(i), false, false));
        differentPostions.add(i);
      }
    }
    if (!differentPostions.isEmpty()) {
      LOG.info(
        "GitProxy difference was found for VCS root(" + LogUtil.describe(root) + "). Different ModificationData at positions " + myGson.toJson(differentPostions)
        + ". diff:{" + myGson.toJson(diff)
        + "}, "
        + getStateDiff(fromState, toState) +
        ", equal:{" + myGson.toJson(getCommitOnlyModificationDataList(jgitData)) + "}"
      );
    }
  }

  private ModificationData createModificationDataGitProxy(@NotNull CommitInfo info, @NotNull CommitChange firstEdgeChange, @NotNull GitVcsRoot gitRoot, @NotNull VcsRoot root, @Nullable List<CommitChange> mergeEdgeChanges) {
    CommitChange change;
    Map<String, LinkedHashMap<String, ChangeType>> perParentChangedFilesForMergeCommit = null;
    if (mergeEdgeChanges == null) {
      change = firstEdgeChange;
    } else {
      perParentChangedFilesForMergeCommit = new HashMap<>();
      addFileChanges(perParentChangedFilesForMergeCommit, firstEdgeChange);
      for (CommitChange edgeChange : mergeEdgeChanges) {
        addFileChanges(perParentChangedFilesForMergeCommit, edgeChange);
      }
      change = inferMergeCommitChange(firstEdgeChange, perParentChangedFilesForMergeCommit);
    }
    List<VcsChange> vcsChanges = change.changes.stream().map(fileChange -> {
      VcsChangeInfo.Type changeType;
      switch (fileChange.changeType) {
        case Added: changeType = VcsChangeInfo.Type.ADDED; break;
        case Deleted: changeType = VcsChangeInfo.Type.REMOVED; break;
        case Modified: changeType = VcsChangeInfo.Type.CHANGED; break;
        default: changeType = VcsChangeInfo.Type.NOT_CHANGED;
      }

      String filePath = fileChange.getDisplayPath();
      return new VcsChange(changeType,
                           null, // TODO identify if file mode has changed and provide description in that case
                           filePath,
                           filePath,
                           (info.parents == null || info.parents.isEmpty()) ? ObjectId.zeroId().name() : info.parents.get(0),
                           info.id);
    }).collect(Collectors.toList());

    String author = GitServerUtil.getUser(gitRoot, new PersonIdent(info.author.name, info.author.email));
    Date authorDate = new Date((long)info.authorTime * 1000);
    ModificationData modificationData = new ModificationData(authorDate, vcsChanges, info.fullMessage, author, root, change.revision, change.revision);

    if (perParentChangedFilesForMergeCommit != null) {
      setMergeCommitAttributes(modificationData, perParentChangedFilesForMergeCommit);
    }
    String commiter = GitServerUtil.getUser(gitRoot, new PersonIdent(info.committer.name, info.committer.email));
    Date commitDate = new Date((long)info.commitTime * 1000);
    if (!Objects.equals(authorDate, commitDate)) {
      modificationData.setAttribute(DBVcsModification.TEAMCITY_COMMIT_TIME, Long.toString(commitDate.getTime()));
    }
    if (!Objects.equals(author, commiter)) {
      modificationData.setAttribute(DBVcsModification.TEAMCITY_COMMIT_USER, commiter);
    }

    modificationData.setParentRevisions(info.parents != null && !info.parents.isEmpty() ? info.parents : Collections.singletonList(ObjectId.zeroId().name()));

    return modificationData;
  }

  private void setMergeCommitAttributes(@NotNull ModificationData modificationData,  @NotNull Map<String, LinkedHashMap<String, ChangeType>> perParentChangedFiles) {
    Map<String, Set<String>> map = new HashMap<>(perParentChangedFiles.size());
    for (Map.Entry<String, LinkedHashMap<String, ChangeType>> entry : perParentChangedFiles.entrySet()) {
      map.put(entry.getKey(), entry.getValue().keySet());
    }
    modificationData.setAttributes(VcsChangeTreeWalk.buildChangedFilesAttributesFor(map));
  }

  private void addFileChanges(@NotNull Map<String, LinkedHashMap<String, ChangeType>> changedFiles, @NotNull CommitChange edgeChange) {
    if (edgeChange.compareTo == null) {
      return;
    }
    LinkedHashMap<String, ChangeType> res = new LinkedHashMap<>(edgeChange.changes.size());
    for (FileChange fileChange : edgeChange.changes) {
      res.put(fileChange.getDisplayPath(), fileChange.changeType);
    }
    changedFiles.put(edgeChange.compareTo, res);
  }

  private CommitChange inferMergeCommitChange(@NotNull CommitChange firstEdgeChange, Map<String, LinkedHashMap<String, ChangeType>> parentChangedFilesMap) {
    List<FileChange> changedFiles = new ArrayList<>();
    for (FileChange fileChange : firstEdgeChange.changes) {
      // check that file is changed by each edge, this means that the change was made in the merge commit
      boolean wasChangedInMergeCommit = true;
      ChangeType changeType = fileChange.changeType;
      for (Map.Entry<String, LinkedHashMap<String, ChangeType>> entry : parentChangedFilesMap.entrySet()) {
        if (!entry.getValue().containsKey(fileChange.getDisplayPath())) {
          wasChangedInMergeCommit = false;
          break;
        }

        // if at least by one edge the file was modified, then the change type should be Modified even if the first edge change type was Added
        if (changeType == ChangeType.Added) {
          ChangeType edgeChangeType = entry.getValue().get(fileChange.getDisplayPath());
          if (edgeChangeType == ChangeType.Modified) {
            changeType = ChangeType.Modified;
          }
        }
      }
      if (wasChangedInMergeCommit) {
        fileChange.changeType = changeType;
        changedFiles.add(fileChange);
      }
    }
    return new CommitChange(firstEdgeChange.revision, firstEdgeChange.compareTo, firstEdgeChange.limitReached, changedFiles);
  }

  @NotNull
  private static String getStateDiff(@NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState) {
    StringBuilder result = new StringBuilder( "State_diff{");
    result.append("FromState[default=").append(fromState.getDefaultBranchName()).append(":").append(fromState.getDefaultBranchRevision()).append("] ");
    result.append("ToState[default=").append(fromState.getDefaultBranchName()).append(":").append(fromState.getDefaultBranchRevision()).append("] ");
    result.append("diff[");

    for (Map.Entry<String, String> entry : fromState.getBranchRevisions().entrySet()) {
      String toStateValue = toState.getBranchRevisions().get(entry.getKey());
      if (!entry.getValue().equals(toStateValue)) {
        result.append(entry.getKey()).append("(").append("f:").append(entry.getValue()).append("|").append("t:").append(toStateValue).append("),");
      }
    }

    for (Map.Entry<String, String> entry : toState.getBranchRevisions().entrySet()) {
      if (!fromState.getBranchRevisions().containsKey(entry.getKey())) {
        result.append(entry.getKey()).append("(").append("f:null|").append("t:").append(entry.getValue()).append("),");
      }
    }
    result.append("]}");
    return result.toString();
  }

  private static boolean notEqualModificationListsById(@NotNull List<ModificationData> data1, @NotNull List<ModificationData> data2) {
    for (int i = 0; i < data1.size(); i++) {
      if (!data1.get(i).getVersion().equals(data2.get(i).getVersion())) {
        return true;
      }
    }
    return false;
  }

  public enum ModDataComparisonResult {
    EQUAL,
    NOT_EQUAL_VCS_CHANGES,
    NOT_EQUAL_ATTRIBUTES,
    NOT_EQUAL_OTHER
  }

  private static ModDataComparisonResult compareModifications(@NotNull ModificationData data1, @NotNull ModificationData data2) {
    if (!data2.getVersion().equals(data1.getVersion())) return ModDataComparisonResult.NOT_EQUAL_OTHER;

    if (data1.getChanges().size() != data2.getChanges().size()) return ModDataComparisonResult.NOT_EQUAL_VCS_CHANGES;
    if (notEqualVcsChanges(data1.getChanges(), data2.getChanges())) {
      List<VcsChange> changes1Sorted = new ArrayList<>(data1.getChanges());
      List<VcsChange> changes2Sorted = new ArrayList<>(data2.getChanges());
      Collections.sort(changes1Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
      Collections.sort(changes2Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
      if (notEqualVcsChanges(changes1Sorted, changes2Sorted)) return ModDataComparisonResult.NOT_EQUAL_VCS_CHANGES;
    }

    if (data1.getChangeCount() != data2.getChangeCount()) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!data2.getDisplayVersion().equals(data1.getDisplayVersion())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    // TODO space supports .mailmap files which can map commit author email to some other email, we do not use it and so the username might differ
    // if (!Objects.equals(data2.getUserName(), data1.getUserName())) return ModDataComparisonResult.NOT_EUQAL_OTHER;
    if (!Objects.equals(data2.getVcsDate(), data1.getVcsDate())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!Objects.equals(data2.getParentRevisions(), data1.getParentRevisions())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!Objects.equals(data2.getAttributes(), data1.getAttributes())) return ModDataComparisonResult.NOT_EQUAL_ATTRIBUTES;
    return ModDataComparisonResult.EQUAL;
  }

  private static boolean notEqualVcsChanges(@NotNull List<VcsChange> changes1, @NotNull List<VcsChange> changes2) {
    for (int i = 0; i < changes2.size(); i++) {
      VcsChange change1 = changes1.get(i);
      VcsChange change2 = changes2.get(i);
      if (!Objects.equals(change1.getType(), change2.getType())) return true;
      if (!Objects.equals(change1.getAfterChangeRevisionNumber(), change2.getAfterChangeRevisionNumber())) return true;
      if (!Objects.equals(change1.getBeforeChangeRevisionNumber(), change2.getBeforeChangeRevisionNumber())) return true;
      if (!Objects.equals(change1.getFileName(), change2.getFileName())) return true;
      if (!Objects.equals(change1.getRelativeFileName(), change2.getRelativeFileName())) return true;
    }
    return false;
  }

  private static List<ModificationData> getCommitOnlyModificationDataList(@NotNull List<ModificationData> modifications) {
    return modifications.stream().map(data -> getDataWithoutFileChanges(data, true, false)).collect(Collectors.toList());
  }

  private static ModificationData getDataWithoutFileChanges(@NotNull ModificationData data, boolean commitOnly, boolean addAttributes) {
    ModificationData updData;
    if (commitOnly) {
      updData = new ModificationData(data.getVcsDate(), Collections.emptyList(), null, null, data.getVcsRoot(), data.getVersion(), null);
    } else {
      updData = new ModificationData(data.getVcsDate(), Collections.emptyList(), data.getDescription(), data.getUserName(), data.getVcsRoot(), data.getVersion(), null);
    }
    if (addAttributes) {
      updData.setAttributes(data.getAttributes());
    }
    updData.setParentRevisions(data.getParentRevisions());
    return updData;
  }
}
