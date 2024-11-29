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
import org.apache.commons.lang.StringUtils;
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

  private static final String GIT_PROXY_REQUEST_TIMEOUT_SECONDS_INTERNAL_PROPERTY = "teamcity.git.gitProxy.requestTimeoutSeconds";
  private static final int GIT_PROXY_REQUEST_TIMEOUT_SECONDS_DEFAULT = 20;
  private static final String GIT_PROXY_CONNECT_TIMEOUT_INTERNAL_PROPERTY = "teamcity.git.gitProxy.connectTimeoutMs";
  private static final int GIT_PROXY_CONNECT_TIMEOUT_DEFAULT = 3000;
  private static final String GIT_PROXY_CONNECT_RETRY_CNT_INTERNAL_PROPERTY = "teamcity.git.gitProxy.connectTimeout.retryCount";
  private static final int GIT_PROXY_CONNECT_RETRY_CNT_DEFAULT = 1;

  // limits on result size and paging
  private static final String GIT_PROXY_COMMITS_PER_PAGE = "teamcity.git.gitProxy.commitsPerPage";
  private static final int GIT_PROXY_COMMITS_PER_PAGE_DEFAULT = 1000;

  private static final String GIT_PROXY_MAX_FILE_CHANGES_PER_COMMIT = "teamcity.git.gitProxy.maxFileChangesPerCommit";
  private static final int GIT_PROXY_MAX_FILE_CHANGES_PER_COMMIT_DEFAULT = 10_000;

  private static final String GIT_PROXY_MAX_RESULT_SIZE_MB = "teamcity.git.gitProxy.maxResultSizeMb";
  private static final int GIT_PROXY_MAX_RESULT_SIZE_MB_DEFAULT = 1000; // 1 GB


  private static final String COMPARE_MODIFICATIONS_WITHOUT_ORDER_LOGGING_PROPERTY = "teamcity.git.gitProxy.logging.unorderedModificationComparison";

  private static final long GIT_COMMIT_ID_SIZE_BYTES = 120; // 40 chars(80 bytes) + 40 bytes approximate string overhead

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
      int requestTimeout = TeamCityProperties.getInteger(GIT_PROXY_REQUEST_TIMEOUT_SECONDS_INTERNAL_PROPERTY, GIT_PROXY_REQUEST_TIMEOUT_SECONDS_DEFAULT) * 1000;
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

  @Nullable
  public GitApiClient<GitRepoApi> getClient(@NotNull GitProxySettings proxySettings, @NotNull VcsRoot root, String operationId) {
    String url = root.getProperty(Constants.FETCH_URL);
    if (url == null) {
      return null;
    }

    // try to parse project name and repository name from url. For now git proxy only works with jetbrains.team repositories
    List<String> path = ServerURIParser.createServerURI(url).getPathFragments();
    if (path.size() == 0) {
      return null;
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
    return myGitApiClientFactory.createRepoApi(proxySettings, null, fullRepoName)
                                .withOperationId(operationId);
  }

  /**
   * @param commitIdToSubmodulePrefixes commit id to list of paths to submodules that were changed in that commit
   * @throws VcsException
   */
  @NotNull
  public List<ModificationData> collectChangesGitProxy(@NotNull VcsRoot root,
                                                        @NotNull RepositoryStateData fromState,
                                                        @NotNull RepositoryStateData toState,
                                                        @NotNull GitProxySettings proxyCredentials,
                                                        @NotNull String operationId,
                                                        @Nullable Map<String, List<String>> commitIdToSubmodulePrefixes) throws VcsException {
    GitVcsRoot gitRoot = new SGitVcsRoot(myRepositoryManager, root, new URIishHelperImpl(), null);
    GitApiClient<GitRepoApi> client = getClient(proxyCredentials, root, operationId);
    if (client == null) {
      return Collections.emptyList();
    }

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

    Map<String, CommitInfo> commitInfoMap = new HashMap<>();
    List<CommitChange> changes = retrieveChanges(client, commitPatterns, commitInfoMap);

    List<ModificationData> result = new ArrayList<>();
    int i = changes.size() - 1;
    // traverse the list of changes from the end and remove elements from the end of 'changes' list after processing to make it possible for gc to collect those objects later
    while (i >= 0) {
      CommitInfo info = commitInfoMap.get(changes.get(i).revision);
      List<CommitChange> mergeEdgeChanges = null;
      // find diff for other edges of merge commit, when inferMergeCommitChanges was set to false separate commit changes are returned for each edge of the merge commit
      while (i - 1 >= 0 && changes.get(i - 1).revision.equals(info.id)) {
        if (mergeEdgeChanges == null) {
          mergeEdgeChanges = new ArrayList<>();
        }
        mergeEdgeChanges.add(changes.get(i));
        changes.remove(i);  // remove last object for gc
        i--;
      }
      if (mergeEdgeChanges != null) {
        Collections.reverse(mergeEdgeChanges);
      }
      CommitChange change = changes.get(i);
      changes.remove(i); //remove last object for gc
      i--;
      if (info == null) {
        LOG.error("There is no commit info for returned revision " + change.revision + "Operation id " + operationId);
        continue;
      }
      commitInfoMap.remove(change.revision); // remove for gc
      result.add(createModificationDataGitProxy(info, change, gitRoot, root, mergeEdgeChanges, commitIdToSubmodulePrefixes));
    }
    Collections.reverse(result);
    return result;
  }

  private List<CommitChange> retrieveChanges(@NotNull GitApiClient<GitRepoApi> client, @NotNull List<String> commitPatterns, @NotNull Map<String, CommitInfo> commitInfoMap) throws VcsException {
    List<CommitChange> changes = new ArrayList<>();
    int maxCommitsPerPage = TeamCityProperties.getInteger(GIT_PROXY_COMMITS_PER_PAGE, GIT_PROXY_COMMITS_PER_PAGE_DEFAULT);

    long currentResultSize = 0;
    long startTime = System.currentTimeMillis();
    boolean shouldCollectFileChanges = true;
    while (true) {
      if (shouldCollectFileChanges && currentResultSize > getMaxChangesCollectionResultSizeInBytes()) {
        LOG.warn(String.format("Failed to collect all the changes from git proxy. Reached the size limit of changes collection result. File changes will not be collected starting from revision %s. Operation id %s",
                               changes.get(changes.size() - 1).revision, client.getOperationId()));
        shouldCollectFileChanges = false;
      }
      if (!shouldCollectFileChanges && currentResultSize > 2 * getMaxChangesCollectionResultSizeInBytes()) {
        LOG.warn(String.format("Failed to collect all the changes from git proxy. Reached the maximum size of changes collection result. Returning partial result ending with %s. Operation id %s",
                               changes.get(changes.size() - 1).revision, client.getOperationId()));
        return changes;
      }

      CommitList commitList;
      try {
        commitList = client.newRequest().listCommits(Collections.singletonList(new Pair<>("id-range", commitPatterns)), changes.size(), maxCommitsPerPage, false, true);
      } catch (Exception e) {
        throw new VcsException("Failed to collect commits from git proxy for collectChanges operation", e);
      }

      if (System.currentTimeMillis() - startTime > TeamCityProperties.getLong(GIT_PROXY_REQUEST_TIMEOUT_SECONDS_INTERNAL_PROPERTY, GIT_PROXY_REQUEST_TIMEOUT_SECONDS_DEFAULT) * 1000) {
        throw new VcsException(String.format("Failed to collect all the changes from git proxy in specified time. Retrieved: %d, Total matched: %d", changes.size(), commitList.totalMatched));
      }

      if (shouldCollectFileChanges) {
        List<String> commitIds = new ArrayList<>(commitList.commits.size());

        for (Commit commit : commitList.commits) {
          commitInfoMap.put(commit.id, commit.info);
          commitIds.add(commit.id);

          // size of data in commitInfoMap
          currentResultSize += GIT_COMMIT_ID_SIZE_BYTES * 2; // commit.id, commit.info.id
          currentResultSize += getStringSizeBytes(commit.info.fullMessage);
          currentResultSize += commit.info.parents == null ? 0 : commit.info.parents.size() * GIT_COMMIT_ID_SIZE_BYTES;
        }

        try {
          List<CommitChange> newChanges = client.newRequest().listChanges(commitIds, false, false, false, false,
                                                                          TeamCityProperties.getInteger(GIT_PROXY_MAX_FILE_CHANGES_PER_COMMIT,
                                                                                                        GIT_PROXY_MAX_FILE_CHANGES_PER_COMMIT_DEFAULT));
          for (CommitChange change : newChanges) {
            changes.add(change);
            currentResultSize += GIT_COMMIT_ID_SIZE_BYTES; // change.revision
            currentResultSize += change.compareTo == null ? 0 : GIT_COMMIT_ID_SIZE_BYTES;
            for (FileChange fileChange : change.changes) {
              currentResultSize += getStringSizeBytes(fileChange.oldPath);
              currentResultSize += getStringSizeBytes(fileChange.newPath);
            }
          }
        } catch (Exception e) {
          throw new VcsException("Failed to collect changes from git proxy for collectChanges operation", e);
        }
      } else {
        for (Commit commit : commitList.commits) {
          String parent = commit.info.parents.isEmpty() ? null : commit.info.parents.get(0);

          currentResultSize += GIT_COMMIT_ID_SIZE_BYTES;
          currentResultSize += parent == null ? 0 : GIT_COMMIT_ID_SIZE_BYTES;
          changes.add(new CommitChange(commit.id, parent, false, Collections.emptyList()));
        }
      }

      if (changes.size() >= commitList.totalMatched) {
        // all the result pages were retrieved, we can return result
        break;
      }
    }

    return changes;
  }

  private long getMaxChangesCollectionResultSizeInBytes() {
    return TeamCityProperties.getLong(GIT_PROXY_MAX_RESULT_SIZE_MB, GIT_PROXY_MAX_RESULT_SIZE_MB_DEFAULT) * 1_000_000;
  }

  private long getStringSizeBytes(@Nullable String s) {
    if (s == null) {
      return 0;
    }
    return s.length() * 2L + 40;
  }

  private void logDiffLength(@NotNull List<ModificationData> jgitData, @NotNull List<ModificationData> gitProxyData, @NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState, @NotNull VcsRoot root, @NotNull String additionalData) {
    LOG.info("GitProxy difference was found for VCS root(" + LogUtil.describe(root) + "). Different length." + additionalData + " jgit:{" + myGson.toJson(getCommitOnlyModificationDataList(jgitData)) + "}, gitProxy:{" + myGson.toJson(getCommitOnlyModificationDataList(gitProxyData)) + "}," + getStateDiff(fromState, toState));
  }

  enum ExtraDataState {
    FORCE_PUSHED,
    UNKNOWN
  }

  private boolean doesCommitStillExist(@NotNull String version, GitApiClient<GitRepoApi> repoApi) {
    CommitList res = repoApi.newRequest().listCommits(Collections.singletonList(new Pair<>("id", Collections.singletonList(version))), 0, Integer.MAX_VALUE, false, false);
    return res.totalMatched > 0;
  }

  public void logAnyDifferences(@NotNull List<ModificationData> jgitData, @NotNull List<ModificationData> gitProxyData,
                                @NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState,
                                @NotNull VcsRoot root, @NotNull GitApiClient<GitRepoApi> gitRepoApi,
                                @NotNull Map<String, List<String>> submodulePrefixes) {
    long startTime = System.currentTimeMillis();
    if (jgitData.size() != gitProxyData.size()) {
      if (jgitData.size() == 0) {
        logDiffLength(jgitData, gitProxyData, fromState, toState, root, " Jgit result is empty.");
        return;
      }
      Map<String, ModificationData> gitProxyDatMap = new HashMap<>(gitProxyData.size());
      Map<String, ModificationData> jgitDataMap = new HashMap<>(jgitData.size());
      Map<String, String> jgitReverseEdges = new HashMap<>(jgitData.size());
      for (ModificationData data: gitProxyData) {
        gitProxyDatMap.put(data.getVersion(), data);
      }

      Map<String, ExtraDataState> jgitExtraData = new HashMap<>();
      for (ModificationData data: jgitData) {
        jgitDataMap.put(data.getVersion(), data);
        if (!gitProxyDatMap.containsKey(data.getVersion())) {
          jgitExtraData.put(data.getVersion(), ExtraDataState.UNKNOWN);
        }
        for (String parentVersion : data.getParentRevisions()) {
          jgitReverseEdges.put(parentVersion, data.getVersion());
        }
      }

      for (ModificationData data: gitProxyData) {
        if (!jgitDataMap.containsKey(data.getVersion())) {
          logDiffLength(jgitData, gitProxyData, fromState, toState, root, " gitProxy result has extra commits.");
          return;
        }
      }

      for (String bottomModVersion: jgitExtraData.keySet()) {
        if (System.currentTimeMillis() - startTime > 10000) {
          throw new RuntimeException("Compare diff timeout");
        }
        ModificationData data = jgitDataMap.get(bottomModVersion);
        if (data.getParentRevisions().stream().anyMatch(parent -> jgitDataMap.containsKey(parent))) {
          // this is not the bottom modification in the returned result
          continue;
        }

        // try to find the top modification from which this modification is reachable
        String parentVersion = data.getVersion();
        List<ModificationData> branchModifications = new ArrayList<>();
        // this var is to avoid visiting the same commits twice
        boolean forcePushed = false;
        while (parentVersion != null) {
          if (System.currentTimeMillis() - startTime > 10000) {
            throw new RuntimeException("Compare diff timeout");
          }
          if (jgitExtraData.containsKey(parentVersion) && jgitExtraData.get(parentVersion) == ExtraDataState.FORCE_PUSHED) {
            forcePushed = true;
            break;
          }
          branchModifications.add(jgitDataMap.get(parentVersion));
          parentVersion = jgitReverseEdges.get(parentVersion);
        }

        if (!forcePushed) {
          ModificationData topModification = branchModifications.get(branchModifications.size() - 1);
          if (!gitProxyDatMap.containsKey(topModification.getVersion())) {
            // this is likely the case when the branch was rolled back, it is expected to have empty result from git proxy
            // check if commit still exists
            if (doesCommitStillExist(topModification.getVersion(), gitRepoApi)) {
              logDiffLength(jgitData, gitProxyData, fromState, toState, root,
                            String.format(" Version %s still exists, but gitProxy didn't return it.", topModification.getVersion()));
              return;
            }
          } else {
            // we need to find the corresponding from version for this branch to check if jgit returned more commits because of force push
            String topFromRevision = null;
            for (Map.Entry<String, String> toEntry : toState.getBranchRevisions().entrySet()) {
              if (topModification.getVersion().equals(toEntry.getValue())) {
                String fromRev = fromState.getBranchRevisions().get(toEntry.getKey());
                if (fromRev != null && !fromRev.equals(topModification.getVersion())) {
                  topFromRevision = fromRev;
                  break;
                }
              }
            }

            if (topFromRevision == null) {
              logDiffLength(jgitData, gitProxyData, fromState, toState, root,
                            String.format(" From state revision wasn't found for to state revision %s.", topModification.getVersion()));
              return;
            }

            if (doesCommitStillExist(topFromRevision, gitRepoApi)) {
              logDiffLength(jgitData, gitProxyData, fromState, toState, root, String.format(" From state revision %s still exists.", topFromRevision));
              return;
            }
          }
        }

        // mark commits as force pushed so we won't try to process them again
        branchModifications.forEach(mod -> {
          if (jgitExtraData.containsKey(mod.getVersion())) {
            jgitExtraData.put(mod.getVersion(), ExtraDataState.FORCE_PUSHED);
          }
        });
      }
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
      ModDataComparisonResult cmpRes = compareModifications(jgitData.get(i), gitProxyData.get(i), submodulePrefixes.get(jgitData.get(i).getVersion()));
      if (cmpRes == ModDataComparisonResult.NOT_EQUAL_VCS_CHANGES) {
        diff.add(jgitData.get(i));
        diff.add(gitProxyData.get(i));
        differentPostions.add(i);
      }
      else if (cmpRes == ModDataComparisonResult.NOT_EQUAL_ATTRIBUTES) {
        diff.add(getDataWithoutFileChanges(jgitData.get(i), false, true, gitProxyData.get(i), false));
        diff.add(getDataWithoutFileChanges(gitProxyData.get(i), false, true, jgitData.get(i), true));
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

  private ModificationData createModificationDataGitProxy(@NotNull CommitInfo info, @NotNull CommitChange firstEdgeChange, @NotNull GitVcsRoot gitRoot, @NotNull VcsRoot root,
                                                          @Nullable List<CommitChange> mergeEdgeChanges, @Nullable Map<String, List<String>> submodulePrefixesMap) {
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

    List<VcsChange> vcsChanges = new ArrayList<>(change.changes.size());
    for (FileChange fileChange : change.changes) {
      VcsChangeInfo.Type changeType;
      switch (fileChange.changeType) {
        case Added: changeType = VcsChangeInfo.Type.ADDED; break;
        case Deleted: changeType = VcsChangeInfo.Type.REMOVED; break;
        case Modified: changeType = VcsChangeInfo.Type.CHANGED; break;
        default: changeType = VcsChangeInfo.Type.NOT_CHANGED;
      }

      String filePath = fileChange.getDisplayPath();
      if (fileChange.entryType == EntryType.GitLink) {
        if (submodulePrefixesMap != null) {
          submodulePrefixesMap.computeIfAbsent(info.id, (k) -> new ArrayList<>()).add(filePath + "/");
        }
        // we don't list changes in submodules for now
        continue;
      }

     vcsChanges.add(new VcsChange(changeType,
                           null, // TODO identify if file mode has changed and provide description in that case
                           filePath,
                           filePath,
                           (info.parents == null || info.parents.isEmpty()) ? ObjectId.zeroId().name() : info.parents.get(0),
                           info.id));
    }

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
  public static String getStateDiff(@NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState) {
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

  private static ModDataComparisonResult compareModifications(@NotNull ModificationData data1, @NotNull ModificationData data2, @Nullable List<String> submodulePrefixes) {
    if (!data2.getVersion().equals(data1.getVersion())) return ModDataComparisonResult.NOT_EQUAL_OTHER;

    List<VcsChange> changes1Sorted = new ArrayList<>(data1.getChanges());
    List<VcsChange> changes2Sorted = new ArrayList<>(data2.getChanges());
    Collections.sort(changes1Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
    Collections.sort(changes2Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
    if (notEqualVcsChanges(changes1Sorted, changes2Sorted, submodulePrefixes)) return ModDataComparisonResult.NOT_EQUAL_VCS_CHANGES;

    if (data1.getChangeCount() != data2.getChangeCount()) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!data2.getDisplayVersion().equals(data1.getDisplayVersion())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    // TODO space supports .mailmap files which can map commit author email to some other email, we do not use it and so the username might differ
    // if (!Objects.equals(data2.getUserName(), data1.getUserName())) return ModDataComparisonResult.NOT_EUQAL_OTHER;
    if (!Objects.equals(data2.getVcsDate(), data1.getVcsDate())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!Objects.equals(data2.getParentRevisions(), data1.getParentRevisions())) return ModDataComparisonResult.NOT_EQUAL_OTHER;
    if (!Objects.equals(data2.getAttributes(), data1.getAttributes())) return ModDataComparisonResult.NOT_EQUAL_ATTRIBUTES;
    return ModDataComparisonResult.EQUAL;
  }

  private static boolean notEqualVcsChanges(@NotNull List<VcsChange> changesJgit, @NotNull List<VcsChange> changesGitProxy, @Nullable List<String> submodulePrefixes) {

    // currently we don't expect git proxy to return file changes in submodules, so we need to filter them out
    // TODO remove this if we start supporting submodules
    if (submodulePrefixes != null) {
      changesJgit = changesJgit.stream().filter(change -> {
        for (String prefix : submodulePrefixes) {
          if (change.getFileName().startsWith(prefix)) {
            return false;
          }
        }
        return true;
      }).collect(Collectors.toList());
    }

    if (changesJgit.size() != changesGitProxy.size()) return true;


    for (int i = 0; i < changesGitProxy.size(); i++) {
      VcsChange change1 = changesJgit.get(i);
      VcsChange change2 = changesGitProxy.get(i);
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
    return getDataWithoutFileChanges(data, commitOnly, addAttributes, null, false);
  }


  private static ModificationData getDataWithoutFileChanges(@NotNull ModificationData data, boolean commitOnly, boolean addAttributes, @Nullable ModificationData otherData, boolean attrOnlyDiff) {
    ModificationData updData;
    if (commitOnly) {
      updData = new ModificationData(data.getVcsDate(), Collections.emptyList(), null, null, data.getVcsRoot(), data.getVersion(), null);
    } else {
      updData = new ModificationData(data.getVcsDate(), Collections.emptyList(), data.getDescription(), data.getUserName(), data.getVcsRoot(), data.getVersion(), null);
    }
    if (addAttributes) {
      if (otherData != null) {
        HashMap<String, String> attributes = new HashMap<>();
        Map<String, String> otherAttributes = otherData.getAttributes();
        for (Map.Entry<String, String> entry : data.getAttributes().entrySet()) {
          if (!otherAttributes.containsKey(entry.getKey())) {
            attributes.put(entry.getKey(), "other doesn't contain attribute");
          } else {
            if ("teamcity.commit.user".equals(entry.getKey())) {
              // TODO for now we don't log differences in user names, because Space uses mailmap and jgit doesn't
              continue;
            }
            String otherValue = otherAttributes.get(entry.getKey());
            if (!otherValue.equals(entry.getValue())) {
              if (attrOnlyDiff) {
                attributes.put(entry.getKey(), String.format("Difference starting from position %d: %s",
                                                             StringUtils.indexOfDifference(otherValue, entry.getValue()),
                                                             StringUtils.difference(otherValue, entry.getValue())));
              } else {
                attributes.put(entry.getKey(), entry.getValue());
              }
            }
          }
        }
        updData.setAttributes(attributes);
      } else {
        updData.setAttributes(data.getAttributes());
      }
    }
    updData.setParentRevisions(data.getParentRevisions());
    return updData;
  }
}
