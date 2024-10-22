

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitApiClientFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitRepoApi;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.ProxyCredentials;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.CommitChange;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.CommitInfo;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.CommitList;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.FileChange;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleException;
import jetbrains.buildServer.metrics.*;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.DBVcsModification;
import jetbrains.buildServer.vcshostings.url.ServerURIParser;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCollectChangesPolicy implements CollectChangesBetweenRepositories, RevisionMatchedByCheckoutRulesCalculator {

  private static final String GIT_PROXY_URL_PROPERTY = "teamcity.internal.git.gitProxy.url";
  private static final String GIT_PROXY_AUTH_PROPERTY = "teamcity.internal.git.gitProxy.auth";
  private static final String ENABLE_CHANGES_COLLECTION_LOGGING = "teamcity.internal.git.changesCollectionTimeLogging.enabled";
  private static final String ENABLE_GIT_PROXY_COMPARISON_LOGGING = "teamcity.internal.git.gitProxy.changesCollectionComparison.enabled";

  private static final String COMPARE_MODIFICATIONS_WITHOUT_ORDER_LOGGING_PROPERTY = "teamcity.git.gitProxy.logging.unorderedModificationComparison";

  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());

  private final GitVcsSupport myVcs;
  private final VcsOperationProgressProvider myProgressProvider;
  private final ServerPluginConfig myConfig;
  private final RepositoryManager myRepositoryManager;
  private final CheckoutRulesLatestRevisionCache myCheckoutRulesLatestRevisionCache;
  private final Counter myCollectChangesMetric;
  private final Counter myComputeRevisionMetric;
  private final GitApiClientFactory myGitApiClientFactory;
  private final ParameterFactory myParameterFactory;
  private final Gson myGson;

  public GitCollectChangesPolicy(@NotNull GitVcsSupport vcs,
                                 @NotNull VcsOperationProgressProvider progressProvider,
                                 @NotNull ServerPluginConfig config,
                                 @NotNull RepositoryManager repositoryManager,
                                 @NotNull CheckoutRulesLatestRevisionCache checkoutRulesLatestRevisionCache,
                                 @NotNull GitApiClientFactory gitApiClientFactory,
                                 @NotNull ParameterFactory parameterFactory) {
    myVcs = vcs;
    myProgressProvider = progressProvider;
    myConfig = config;
    myRepositoryManager = repositoryManager;
    myCheckoutRulesLatestRevisionCache = checkoutRulesLatestRevisionCache;
    myParameterFactory = parameterFactory;
    ServerMetrics serverMetrics = vcs.getServerMetrics();
    if (serverMetrics != null) {
      myCollectChangesMetric = serverMetrics.metricBuilder("vcs.git.collectChanges.duration")
                                            .description("Git plugin collect changes duration")
                                            .dataType(MetricDataType.MILLISECONDS)
                                            .experimental(true)
                                            .buildCounter();
      myComputeRevisionMetric = serverMetrics.metricBuilder("vcs.git.computeRevision.duration")
                                             .description("Git plugin compute revision by checkout rules duration")
                                             .dataType(MetricDataType.MILLISECONDS)
                                             .experimental(true)
                                             .buildCounter();
    } else {
      myCollectChangesMetric = myComputeRevisionMetric = new NoOpCounter();
    }

    myGitApiClientFactory = gitApiClientFactory;
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


  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot fromRoot,
                                               @NotNull RepositoryStateData fromState,
                                               @NotNull VcsRoot toRoot,
                                               @NotNull RepositoryStateData toState,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    return collectChanges(toRoot, fromState, toState, checkoutRules);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull RepositoryStateData fromState,
                                               @NotNull RepositoryStateData toState,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    SProject project = retrieveProject(root);
    try (Stoppable stoppable = myCollectChangesMetric.startMsecsTimer()) {
      final ProxyCredentials proxyCredentials = getGitProxyInfo(root, project, "");
      if (proxyCredentials != null) {
        return runCollectChangesWithTimer("gitProxy", root, project, false, () -> collectChangesGitProxy(root, fromState, toState, proxyCredentials));
      } else {
        List<ModificationData> jgitResult = runCollectChangesWithTimer("jgit", root, project, false, () -> collectChangesJgit(root, fromState, toState));
        if (project != null && Boolean.parseBoolean(project.getParameterValue(ENABLE_GIT_PROXY_COMPARISON_LOGGING))) {
          try {
            ProxyCredentials testProxyCredentials = getGitProxyInfo(root, project, ".comparisonTest");
            if (testProxyCredentials != null) {
              List<ModificationData> gitProxyResult =
                runCollectChangesWithTimer("gitProxy", root, project, true, () -> collectChangesGitProxy(root, fromState, toState, testProxyCredentials));
              logAnyDifferences(jgitResult, gitProxyResult, fromState, toState, root);
            }
          } catch (Throwable t) {
            LOG.error("Failed to compare gitProxy and jgit changes collection results", t);
          }
        }
        return jgitResult;
      }
    }
  }

  private List<ModificationData> runCollectChangesWithTimer(@NotNull String methodName, @NotNull VcsRoot root, @Nullable SProject project, boolean safeMode, Callable<List<ModificationData>> operation) throws VcsException {
    long startTime = System.currentTimeMillis();
    List<ModificationData> result;
    try {
      result = operation.call();
    } catch (Exception e) {
      logChangesCollectionOperationData(methodName, root, project, System.currentTimeMillis() - startTime, e);
      if (!safeMode) {
        if (e instanceof VcsException) {
          throw (VcsException)e;
        }
        throw new VcsException(e);
      } else {
        return Collections.emptyList();
      }
    }

    logChangesCollectionOperationData(methodName, root, project, System.currentTimeMillis() - startTime, null);

    return result;
  }

  private void logAnyDifferences(@NotNull List<ModificationData> jgitData, @NotNull List<ModificationData> gitProxyData, @NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState, @NotNull VcsRoot root) {
    if (jgitData.size() != gitProxyData.size()) {
      LOG.info("GitProxy difference was found for VCS root(" + LogUtil.describe(root) + "). Different length. jgit:{" + myGson.toJson(jgitData) + "}, gitProxy:{" + myGson.toJson(gitProxyData) + "}," + getStateDiff(fromState, toState));
      return;
    }

    if (TeamCityProperties.getBoolean(COMPARE_MODIFICATIONS_WITHOUT_ORDER_LOGGING_PROPERTY) && notEqualModificationListsById(jgitData, gitProxyData)) {
      jgitData = new ArrayList<>(jgitData);
      gitProxyData = new ArrayList<>(gitProxyData);
      Collections.sort(jgitData, (a, b) -> a.getVersion().compareTo(b.getVersion()));
      Collections.sort(gitProxyData, (a, b) -> a.getVersion().compareTo(b.getVersion()));
    }
    for (int i = 0; i < jgitData.size(); i++) {
      if (notEqualModifications(jgitData.get(i), gitProxyData.get(i))) {
        LOG.info(
          "GitProxy difference was found for VCS root(" + LogUtil.describe(root) + "). Different ModificationData at position " + i + ". jgit:{" + myGson.toJson(jgitData) +
          "}, gitProxy:{" + myGson.toJson(gitProxyData) + "}," + getStateDiff(fromState, toState));
        return;
      }
    }
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

  private static boolean notEqualModifications(@NotNull ModificationData data1, @NotNull ModificationData data2) {
    if (data1.getChangeCount() != data2.getChangeCount()) return true;
    if (!data2.getDisplayVersion().equals(data1.getDisplayVersion())) return true;
    if (!data2.getVersion().equals(data1.getVersion())) return true;
    if (!Objects.equals(data2.getUserName(), data1.getUserName())) return true;
    if (!Objects.equals(data2.getVcsDate(), data1.getVcsDate())) return true;
    if (!Objects.equals(data2.getParentRevisions(), data1.getParentRevisions())) return true;
    if (!Objects.equals(data2.getAttributes(), data1.getAttributes())) return true;

    if (data1.getChanges().size() != data2.getChanges().size()) return true;

    if (notEqualVcsChanges(data1.getChanges(), data2.getChanges())) {
      List<VcsChange> changes1Sorted = new ArrayList<>(data1.getChanges());
      List<VcsChange> changes2Sorted = new ArrayList<>(data2.getChanges());
      Collections.sort(changes1Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
      Collections.sort(changes2Sorted, (a, b) -> a.getFileName().compareTo(b.getFileName()));
      if (notEqualVcsChanges(changes1Sorted, changes2Sorted)) return true;
    }
    return false;
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

  private void logChangesCollectionOperationData(@NotNull String methodName, @NotNull VcsRoot root, @Nullable SProject project, long time, @Nullable Exception e) {
    if (project != null && Boolean.parseBoolean(project.getParameterValue(ENABLE_CHANGES_COLLECTION_LOGGING))) {
      LOG.info(String.format("Changes collection(%s) operation for Project %s, VCS Root %s: %d ms.",
                             methodName, project.getProjectId(), root.getExternalId(), time) + (e == null ? "" : " Finished with exception"), e);
    }
  }

  @Nullable
  private SProject retrieveProject(@NotNull VcsRoot root) {
    try {
      if (!(root instanceof VcsRootInstance)) {
        return null;
      }

      return ((VcsRootInstance)root).getParent().getProject();
    } catch (Throwable e) {
      return null;
    }
  }

  @Nullable
  private ProxyCredentials getGitProxyInfo(@NotNull VcsRoot root, @Nullable SProject project, @NotNull String suffix) {
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
      if (urlParam != null && authParam != null) {
        return new ProxyCredentials(urlParam.getValue(), myParameterFactory.getRawValue(authParam));
      }
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Failed to determine if git proxy should be used", e);
    }
    return null;
  }

  private List<ModificationData> collectChangesGitProxy(@NotNull VcsRoot root,
                                                        @NotNull RepositoryStateData fromState,
                                                        @NotNull RepositoryStateData toState,
                                                        @NotNull ProxyCredentials proxyCredentials) throws VcsException {
    GitVcsRoot gitRoot = new SGitVcsRoot(myRepositoryManager, root, new URIishHelperImpl(), null);

    String url = root.getProperty(Constants.FETCH_URL);
    if (url == null) {
      return Collections.emptyList();
    }

    // try to parse project name and repository name from url. For now git proxy only works with jetbrains.team repositories
    List<String> path = ServerURIParser.createServerURI(url).getPathFragments();
    if (path.size() < 2) {
      return Collections.emptyList();
    }
    String repoName = path.get(path.size() - 1);
    if (repoName.endsWith(".git")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }
    String fullRepoName = String.format("%s/%s", path.get(path.size() - 2), repoName);
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

  private ModificationData createModificationDataGitProxy(@NotNull CommitInfo info, @NotNull CommitChange firstEdgeChange, @NotNull GitVcsRoot gitRoot, @NotNull VcsRoot root, @Nullable List<CommitChange> mergeEdgeChanges) {
    CommitChange change;
    Map<String, LinkedHashSet<String>> perParentChangedFilesForMergeCommit = null;
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

    modificationData.setParentRevisions(info.parents != null ? info.parents : Collections.singletonList(ObjectId.zeroId().name()));

    return modificationData;
  }

  private void setMergeCommitAttributes(@NotNull ModificationData modificationData,  @NotNull Map<String, LinkedHashSet<String>> perParentChangedFiles) {
    modificationData.setAttributes(VcsChangeTreeWalk.buildChangedFilesAttributesFor(perParentChangedFiles));
  }

  private void addFileChanges(@NotNull Map<String, LinkedHashSet<String>> changedFiles, @NotNull CommitChange edgeChange) {
    if (edgeChange.compareTo == null) {
      return;
    }
    changedFiles.put(edgeChange.compareTo, edgeChange.changes.stream().map(fileChange -> fileChange.getDisplayPath()).collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private CommitChange inferMergeCommitChange(@NotNull CommitChange firstEdgeChange, Map<String, LinkedHashSet<String>> parentChangedFilesMap) {
    List<FileChange> changedFiles = new ArrayList<>();
    for (FileChange fileChange : firstEdgeChange.changes) {
      // check that file is changed by each edge, this means that the change was made in the merge commit
      if (parentChangedFilesMap.entrySet().stream().allMatch(entry -> entry.getValue().contains(fileChange.getDisplayPath()))) {
        changedFiles.add(fileChange);
      }
    }
    return new CommitChange(firstEdgeChange.revision, firstEdgeChange.compareTo, firstEdgeChange.limitReached, changedFiles);
  }

  private List<ModificationData> collectChangesJgit(@NotNull VcsRoot root,
                                                    @NotNull RepositoryStateData fromState,
                                                    @NotNull RepositoryStateData toState) throws VcsException {
    OperationContext context = myVcs.createContext(root, "collecting changes", createProgress());
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      List<ModificationData> changes = new ArrayList<ModificationData>();
      try {
        Repository r = context.getRepository();
        ModificationDataRevWalk revWalk = new ModificationDataRevWalk(myConfig, context);
        revWalk.sort(RevSort.TOPO);
        ensureRepositoryStateLoadedFor(context, fromState, toState);
        markStart(r, revWalk, toState);
        markUninteresting(r, revWalk, fromState, toState);
        while (revWalk.next() != null) {
          changes.add(revWalk.createModificationData());

          final int limit = TeamCityProperties.getInteger("teamcity.git.collectChanges.maxChanges", Integer.MAX_VALUE);
          if (changes.size() >= limit) {
            List<String> updatedBranches = getInterestingBranches(fromState, toState);
            LOG.warn("Reached the limit (" + limit + ") for the number of collected changes for VCS root: " + gitRoot.toString() + ", while collecting changes from state: " +
                     shortRepoStateDetails(fromState, updatedBranches) + ", to state: " + shortRepoStateDetails(toState, updatedBranches));
            return changes;
          }
        }
      } catch (Exception e) {
        if (e instanceof SubmoduleException) {
          SubmoduleException se = (SubmoduleException) e;
          Set<String> affectedBranches = getBranchesWithCommit(context.getRepository(), toState, se.getMainRepositoryCommit());
          throw context.wrapException(se.addBranches(affectedBranches));
        }
        throw context.wrapException(e);
      } finally {
        context.close();
      }
      return changes;
    });
  }

  @NotNull
  private String shortRepoStateDetails(@NotNull RepositoryStateData state, @NotNull List<String> updatedBranches) {
    StringBuilder result = new StringBuilder();
    result.append("RepositoryState{");
    result.append("defaultBranch='").append(state.getDefaultBranchName()).append("', ");
    result.append("revisions={");
    Map<String, String> revisions = state.getBranchRevisions();
    Iterator<String> iterator = updatedBranches.iterator();
    while (iterator.hasNext()) {
      String branch = iterator.next();
      result.append(branch).append(": ").append(revisions.get(branch));
      if (iterator.hasNext())
        result.append(", ");
    }
    int diff = revisions.size() - updatedBranches.size();
    if (diff > 0) {
      result.append(", and ").append(diff).append(" more unchanged ").append(StringUtil.pluralize("branch", diff));
    }
    result.append("}}");
    return result.toString();
  }

  @NotNull
  private List<String> getInterestingBranches(@NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState) {
    Set<String> updatedBranches = new HashSet<String>();
    Map<String, String> fromBranches = fromState.getBranchRevisions();
    Map<String, String> toBranches = toState.getBranchRevisions();
    for (Map.Entry<String, String> e : fromBranches.entrySet()) {
      String branchName = e.getKey();
      String toRevision = toBranches.get(branchName);
      if (!e.getValue().equals(toRevision)) {
        updatedBranches.add(branchName);
      }
    }
    for (Map.Entry<String, String> e : toBranches.entrySet()) {
      String branchName = e.getKey();
      String fromRevision = fromBranches.get(branchName);
      if (!e.getValue().equals(fromRevision)) {
        updatedBranches.add(branchName);
      }
    }

    //always include default branches even when they are not updated
    updatedBranches.add(fromState.getDefaultBranchName());
    updatedBranches.add(toState.getDefaultBranchName());

    List<String> result = new ArrayList<>(updatedBranches);
    Collections.sort(result);
    return result;
  }

  @NotNull
  public Result getLatestRevisionAcceptedByCheckoutRules(@NotNull VcsRoot root,
                                                         @NotNull CheckoutRules rules,
                                                         @NotNull String startRevision,
                                                         @NotNull String startRevisionBranchName,
                                                         @NotNull Collection<String> stopRevisions) throws VcsException {
    return getLatestRevisionAcceptedByCheckoutRules(root, rules, startRevision, startRevisionBranchName, stopRevisions, null);
  }

  @NotNull
  public Result getLatestRevisionAcceptedByCheckoutRules(@NotNull VcsRoot root,
                                                         @NotNull CheckoutRules rules,
                                                         @NotNull String startRevision,
                                                         @NotNull String startRevisionBranchName,
                                                         @NotNull Collection<String> stopRevisions,
                                                         @Nullable Set<String> visited) throws VcsException {
    try (Stoppable stoppable = myComputeRevisionMetric.startMsecsTimer()) {
      Disposable name = NamedDaemonThreadFactory.patchThreadName("Computing the latest commit affected by checkout rules: " + rules +
                                                                 " in VCS root: " + LogUtil.describe(root) + ", start revision: " + startRevision + " (branch: " + startRevisionBranchName + "), stop revisions: " + stopRevisions);
      try {
        OperationContext context = myVcs.createContext(root, "latest revision affecting checkout", createProgress());
        GitVcsRoot gitRoot = context.getGitRoot();
        return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
          ensureRevisionIsFetched(startRevision, startRevisionBranchName, context);

          Result finalResult = null;
          CheckoutRulesLatestRevisionCache.Value cached = myCheckoutRulesLatestRevisionCache.getCachedValue(gitRoot, startRevisionBranchName, rules);
          if (cached != null && cached.myStartRevision.equals(startRevision) && cached.myStopRevisions.equals(stopRevisions)) {
            return new Result(cached.myComputedRevision, cached.myReachedStopRevisions);
          }

          if (cached != null &&
              ((stopRevisions.isEmpty() && cached.myStopRevisions.isEmpty()) || (stopRevisions.containsAll(cached.myStopRevisions) && !cached.myStopRevisions.isEmpty()))) {
            // in this case we can add previous start revision as a stop revision and compute a new result from the new start
            // if the result is null, then we can use the previous result as is
            // otherwise we will return the newly found revision
            Set<String> stops = new HashSet<>(stopRevisions);
            stops.add(cached.myStartRevision);

            Set<String> visitedCommits = new HashSet<>();
            Result result = computeRevisionByCheckoutRules(startRevision, stops, rules, visitedCommits, context, gitRoot);
            if (resultIsValid(result, visitedCommits)) {
              List<String> reachedStops = new ArrayList<>(cached.myReachedStopRevisions);
              reachedStops.retainAll(stopRevisions); // we should only return stop revisions passed to us as an argument
              String computedResult = result.getRevision();

              if (result.getRevision() == null) {
                // no interesting commits since the last start, return previous result
                computedResult = cached.myComputedRevision;
              }

              finalResult = new Result(computedResult, reachedStops);
              if (visited != null) {
                visited.addAll(visitedCommits);
              }
            }
          }

          if (finalResult == null) {
            finalResult = computeRevisionByCheckoutRules(startRevision, stopRevisions, rules, visited, context, gitRoot);
          }
          myCheckoutRulesLatestRevisionCache.storeInCache(gitRoot, rules, startRevision, startRevisionBranchName, stopRevisions, finalResult);
          return finalResult;
        });
      } finally {
        name.dispose();
      }
    }
  }

  private boolean resultIsValid(@NotNull Result result, @NotNull Set<String> visitedCommits) {
    // either we should find some commit or we should at least visit some of the commits
    // if none of these conditions is true then it seems our cached start revision is not reachable
    // from the new start revision (hard reset to some previous commit + force push)
    return result.getRevision() != null || !visitedCommits.isEmpty();
  }

  @NotNull
  private Result computeRevisionByCheckoutRules(@NotNull String startRevision,
                                                @NotNull Collection<String> stopRevisions,
                                                @NotNull CheckoutRules rules,
                                                @Nullable Set<String> visited,
                                                @NotNull OperationContext context,
                                                @NotNull GitVcsRoot gitRoot) throws VcsException {
    CheckoutRulesRevWalk revWalk = null;
    try {
      revWalk = new CheckoutRulesRevWalk(myConfig, context, rules);
      List<RevCommit> startCommits = getCommits(revWalk.getRepository(), revWalk, Collections.singleton(startRevision));
      if (startCommits.isEmpty()) {
        LOG.warn("Could not find the start revision " + startRevision + " in the repository at path: " + gitRoot.getRepositoryDir());
        return new Result(null, Collections.emptyList());
      }

      revWalk.setStartRevision(startCommits.get(0));
      revWalk.setStopRevisions(stopRevisions);

      String result = null;
      RevCommit foundCommit = revWalk.findMatchedCommit();
      if (foundCommit != null) {
        result = foundCommit.name();
      }

      if (visited != null) {
        visited.addAll(revWalk.getVisitedRevisions());
      }

      return new Result(result, revWalk.getReachedStopRevisions());
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      if (revWalk != null) {
        revWalk.close();
        revWalk.dispose();
      }
      context.close();
    }
  }

  private void ensureRevisionIsFetched(@NotNull String revision, @NotNull String branchName, @NotNull OperationContext context) {
    int repeatAttempts = TeamCityProperties.getInteger("teamcity.git.fetchSingleRevision.maxAttempts", 5);
    for (int i=1; i <= repeatAttempts; i++) {
      try {
        Repository repository = context.getRepository();
        ObjectId id = ObjectId.fromString(GitUtils.versionRevision(revision));
        ObjectDatabase objectDatabase = repository.getObjectDatabase();
        if (objectDatabase.has(id)) return;

        objectDatabase.refresh(); // rescan pack files just for the case
        if (objectDatabase.has(id)) return;

        new FetchContext(context, myVcs).withRevisions(Collections.singletonMap(branchName, revision), false).fetchIfNoCommitsOrFail();
        return;
      } catch (Throwable e) {
        boolean repeatable = e instanceof VcsOperationRejectedException;
        String message = "Could not find the start revision " + revision + " in the branch " + branchName;
        if (repeatable && i < repeatAttempts) {
          message += ", will repeat, attempts left: " + (repeatAttempts - i);
        }
        LOG.warnAndDebugDetails(message, e);
        if (!(e instanceof VcsOperationRejectedException)) break;
      }
    }
  }

  @NotNull
  private Set<String> getBranchesWithCommit(@NotNull Repository r, @NotNull RepositoryStateData state, @NotNull String commit) {
    return Collections.emptySet();
  }


  @NotNull
  public RepositoryStateData getCurrentState(@NotNull VcsRoot root) throws VcsException {
    return myVcs.getCurrentState(root);
  }

  public void ensureRepositoryStateLoadedFor(@NotNull final OperationContext context,
                                             @NotNull final RepositoryStateData state,
                                             boolean throwErrors) throws Exception {
    new FetchContext(context, myVcs)
      .withRevisions(state.getBranchRevisions(), throwErrors)
      .fetchIfNoCommitsOrFail();
  }

  public void ensureRepositoryStateLoadedFor(@NotNull final OperationContext context,
                                             @NotNull final RepositoryStateData fromState,
                                             @NotNull final RepositoryStateData toState) throws Exception {
    new FetchContext(context, myVcs)
      .withToRevisions(toState.getBranchRevisions())
      .withFromRevisions(fromState.getBranchRevisions())
      .fetchIfNoCommitsOrFail();
  }

  @NotNull
  public RepositoryStateData fetchAllRefs(@NotNull final OperationContext context,
                                          @NotNull final GitVcsRoot root) throws VcsException {
    try {
      final RepositoryStateData currentState = myVcs.getCurrentState(root);
      new FetchContext(context, myVcs).withFromRevisions(currentState.getBranchRevisions()).fetchIfNoCommitsOrFail();
      return currentState;
    } catch (Exception e) {
      throw new VcsException(e.getMessage(), e);
    }
  }

  private void markUninteresting(@NotNull Repository r,
                                 @NotNull ModificationDataRevWalk walk,
                                 @NotNull final RepositoryStateData fromState,
                                 @NotNull final RepositoryStateData toState) throws IOException {
    List<RevCommit> commits = getCommits(fromState, r, walk);
    if (commits.isEmpty()) {//if non of fromState revisions found - limit commits by toState
      commits = getCommits(toState, r, walk);
      LOG.info("Cannot find commits referenced by fromState, will not report any changes");
    }
    for (RevCommit commit : commits) {
      walk.markUninteresting(commit);
    }
  }


  private void markStart(@NotNull Repository r, @NotNull RevWalk walk, @NotNull RepositoryStateData state) throws IOException {
    walk.markStart(getCommits(state, r, walk));
  }


  @NotNull
  private List<RevCommit> getCommits(@NotNull RepositoryStateData state, @NotNull Repository r, @NotNull RevWalk walk) throws IOException {
    final Collection<String> revisions = state.getBranchRevisions().values();

    return getCommits(r, walk, revisions);
  }

  @NotNull
  private List<RevCommit> getCommits(final @NotNull Repository r, final @NotNull RevWalk walk, @NotNull final Collection<String> revisions)
    throws IOException {
    List<RevCommit> result = new ArrayList<>();
    for (String revision : revisions) {
      ObjectId id = ObjectId.fromString(GitUtils.versionRevision(revision));
      if (r.getObjectDatabase().has(id)) {
        RevObject obj = walk.parseAny(id);
        if (obj.getType() == org.eclipse.jgit.lib.Constants.OBJ_COMMIT)
          result.add((RevCommit) obj);
      }
    }
    return result;
  }


  @NotNull
  private GitProgress createProgress() {
    try {
      return new GitVcsOperationProgress(myProgressProvider.getProgress());
    } catch (IllegalStateException e) {
      return GitProgress.NO_OP;
    }
  }
}