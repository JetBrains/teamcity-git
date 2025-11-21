

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleException;
import jetbrains.buildServer.metrics.*;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCollectChangesPolicy implements CollectChangesBetweenRepositories, CollectChangesWithExtendedResult, RevisionMatchedByCheckoutRulesCalculator {

  private static final String ENABLE_CHANGES_COLLECTION_LOGGING = "teamcity.internal.git.changesCollectionTimeLogging.enabled";
  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());
  public static final String REVISION_BY_CHECKOUT_RULES_USE_LOG_COMMAND = "teamcity.git.checkoutRulesRevision.useLogCommand";

  private final GitVcsSupport myVcs;
  private final VcsOperationProgressProvider myProgressProvider;
  private final ServerPluginConfig myConfig;
  private final RepositoryManager myRepositoryManager;
  private final Counter myCollectChangesMetric;
  private final Counter myComputeRevisionMetric;
  private final GitProxyChangesCollector myGitProxyChangesCollector;

  public GitCollectChangesPolicy(@NotNull GitVcsSupport vcs,
                                 @NotNull VcsOperationProgressProvider progressProvider,
                                 @NotNull ServerPluginConfig config,
                                 @NotNull RepositoryManager repositoryManager,
                                 @NotNull GitApiClientFactory gitApiClientFactory,
                                 @NotNull ParameterFactory parameterFactory,
                                 @NotNull ChangesCollectorCache changesCollectorCache) {
    myVcs = vcs;
    myProgressProvider = progressProvider;
    myConfig = config;
    myRepositoryManager = repositoryManager;
    myGitProxyChangesCollector = new GitProxyChangesCollector(parameterFactory, gitApiClientFactory, repositoryManager, changesCollectorCache);
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
    return collectChangesInternal(root, fromState, toState, false).getModificationDataList();
  }


  @NotNull
  public ChangesCollectionResult collectChangesExtended(@Nullable VcsRoot fromRoot,
                                                        @NotNull RepositoryStateData fromState,
                                                        @NotNull VcsRoot toRoot,
                                                        @NotNull RepositoryStateData toState,
                                                        @NotNull CheckoutRules checkoutRules) throws VcsException {
    return collectChangesInternal(toRoot, fromState, toState, true).toChangesCollectionResult();
  }

  @NotNull
  private GitChangesCollectionResult collectChangesInternal(@NotNull VcsRoot root,
                                                @NotNull RepositoryStateData fromState,
                                                @NotNull RepositoryStateData toState,
                                                boolean allowMissingTips) throws VcsException {
    SProject project = retrieveProject(root);
    String operationId = UUID.randomUUID().toString().substring(0, 8); // used for logging
    try (Stoppable stoppable = myCollectChangesMetric.startMsecsTimer()) {
      final GitProxySettings proxyCredentials = myGitProxyChangesCollector.getGitProxyInfo(root, project);
      if (!GitProxyChangesCollector.isGitProxyEnabled(project) || proxyCredentials == null) {
        GitChangesCollectionResult jgitResult = runCollectChangesWithTimer("jgit", operationId, root, project, false, () -> collectGitChangesJgit(root, fromState, toState));
        if (project != null && GitProxyChangesCollector.isComparisonLoggingEnabled(project) && proxyCredentials != null) {
          try {
            Map<String, List<String>> submodulePrefixesMap = new HashMap<>();
            GitChangesCollectionResult gitProxyResult = runCollectChangesWithTimer("gitProxy", operationId, root, project, true,
                                                                               () -> myGitProxyChangesCollector.collectChangesGitProxy(myConfig, root, fromState, toState, proxyCredentials, operationId, submodulePrefixesMap, false, allowMissingTips));
            GitApiClient<GitRepoApi> api = myGitProxyChangesCollector.getClient(proxyCredentials, root, operationId);
            myGitProxyChangesCollector.logAnyDifferences(jgitResult.getModificationDataList(), gitProxyResult.getModificationDataList(), fromState, toState, root, Objects.requireNonNull(api, "Git repo api can't be null"), submodulePrefixesMap);
          } catch (IgnoredCollectChangesFailure ignored) {
          } catch (Throwable t) {
            LOG.error(String.format("Failed to compare gitProxy and jgit changes collection results. Operation id %s. %s", operationId, GitProxyChangesCollector.getStateDiff(fromState, toState)), t);
          }
        }
        return jgitResult;
      } else {
        // we try to collect changes with gitProxy, if it fails(for example if there were some changes in submodule), then we fall back to jgit changes collection
        try {
          return runCollectChangesWithTimer("gitProxy", operationId, root, project, false,
                                            () -> myGitProxyChangesCollector.collectChangesGitProxy(myConfig, root, fromState, toState, proxyCredentials, operationId, null, true, allowMissingTips));
        } catch (Throwable t) {
          boolean shouldRunFallbackChangesCollections = TeamCityProperties.getBooleanOrTrue(GitProxyChangesCollector.ENABLE_JGIT_FALLBACK_CHANGES_COLLECTION);
          if (t instanceof GitProxyChangesCollector.GitProxySubmoduleChangesNotSupported) {
            LOG.info(String.format("Will not collect changes with gitProxy because changes in submodule were detected, will use jgit changes collection. " +
                                   "State %s. Operation id %s",
                                   GitProxyChangesCollector.getStateDiff(fromState, toState), operationId));
            shouldRunFallbackChangesCollections = true;
          } else {
            LOG.warn(String.format("Failed to collect changes with gitProxy%s. Operation id %s",
                                   shouldRunFallbackChangesCollections ? ", will collect changes with jgit" : "", operationId), t);
          }
          if (shouldRunFallbackChangesCollections) {
            return runCollectChangesWithTimer("jgitFallback", operationId, root, project, false, () -> collectGitChangesJgit(root, fromState, toState));
          } else {
            throw t;
          }
        }
      }
    }
  }

  private static class IgnoredCollectChangesFailure extends RuntimeException { }

  private GitChangesCollectionResult runCollectChangesWithTimer(@NotNull String methodName, @NotNull String operationId, @NotNull VcsRoot root, @Nullable SProject project, boolean safeMode, Callable<GitChangesCollectionResult> operation) throws VcsException {
    long startTime = System.currentTimeMillis();
    GitChangesCollectionResult result;
    try {
      result = operation.call();
    } catch (Exception e) {
      logChangesCollectionOperationData(methodName, operationId, root, project, System.currentTimeMillis() - startTime, e);
      if (!safeMode) {
        if (e instanceof GitProxyChangesCollector.GitProxySubmoduleChangesNotSupported) {
          throw (GitProxyChangesCollector.GitProxySubmoduleChangesNotSupported)e;
        }
        if (e instanceof VcsException) {
          throw (VcsException)e;
        }
        throw new VcsException(e);
      } else {
        throw new IgnoredCollectChangesFailure();
      }
    }

    logChangesCollectionOperationData(methodName, operationId, root, project, System.currentTimeMillis() - startTime, null);

    return result;
  }

  private void logChangesCollectionOperationData(@NotNull String methodName, @NotNull String operationId, @NotNull VcsRoot root, @Nullable SProject project, long time, @Nullable Exception e) {
    if (project != null && Boolean.parseBoolean(project.getParameterValue(ENABLE_CHANGES_COLLECTION_LOGGING))) {
      LOG.info(String.format("Changes collection(%s) operation(Operation id %s) for Project %s, VCS Root %s: %d ms.",
                             methodName, operationId, project.getExternalId(), LogUtil.describe(root), time) + (e == null ? "" : " Finished with exception"), e);
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

  private GitChangesCollectionResult collectGitChangesJgit(@NotNull VcsRoot root,
                                                           @NotNull RepositoryStateData fromState,
                                                           @NotNull RepositoryStateData toState) throws VcsException {
    return new GitChangesCollectionResult(collectChangesJgit(root, fromState, toState), null);
  }

  private List<ModificationData> collectChangesJgit(@NotNull VcsRoot root,
                                                    @NotNull RepositoryStateData fromState,
                                                    @NotNull RepositoryStateData toState) throws VcsException {
    OperationContext context = myVcs.createContext(root, "collecting changes", createProgress());
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      List<ModificationData> changes = new ArrayList<>();
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
    Set<String> updatedBranches = new HashSet<>();
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
      OperationContext context = myVcs.createContext(root, "latest revision affecting checkout", createProgress());
      try {
        GitVcsRoot gitRoot = context.getGitRoot();
        return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
          ensureRevisionIsFetched(startRevision, startRevisionBranchName, context);

          return computeRevisionByCheckoutRules(startRevision, stopRevisions, rules, visited, context, gitRoot);
        });
      } finally {
        context.close();
        name.dispose();
      }
    }
  }

  @NotNull
  private Result computeRevisionByCheckoutRules(@NotNull String startRevision,
                                                @NotNull Collection<String> stopRevisions,
                                                @NotNull CheckoutRules rules,
                                                @Nullable Set<String> visited,
                                                @NotNull OperationContext context,
                                                @NotNull GitVcsRoot gitRoot) throws VcsException {
    if (!stopRevisions.isEmpty() &&
        myVcs.isNativeGitOperationEnabled(gitRoot) &&
        rules.getExcludeRules().isEmpty() &&
        TeamCityProperties.getBoolean(REVISION_BY_CHECKOUT_RULES_USE_LOG_COMMAND)) {

      // this revWalk helps us to compute reachable stop revisions, we do not need to apply checkout rules as we already checked that
      // there are no interesting commits between start and stop revisions
      CheckoutRulesRevWalk revWalk = new CheckoutRulesRevWalk(myConfig, context, rules) {
        @Override
        protected boolean isCurrentCommitIncluded() {
          return false;
        }
      };

      try {
        Boolean hasInterestingCommits = null;
        Collection<String> paths = new HashSet<>();
        try {
          for (IncludeRule r: rules.getIncludeRules()) {
            paths.add(r.getFrom());
          }
          Set<String> stopRevisionsParents = getParentsOfStopRevisions(stopRevisions, context, revWalk);
          if (!stopRevisionsParents.isEmpty()) {
            hasInterestingCommits = hasCommitsAffectingPaths(startRevision, stopRevisionsParents, paths, context, gitRoot);
          }
        } catch (Exception e) {
          // could not compute the changed paths, maybe revisions are invalid
        }

        if (hasInterestingCommits != null && !hasInterestingCommits) {
          try {
            // only traverse DAG to compute visited and reachable stop revisions without checking the checkout rules
            return computeResult(startRevision, stopRevisions, visited, gitRoot, revWalk);
          } catch (Exception e) {
            throw context.wrapException(e);
          }
        }
      } finally {
        revWalk.close();
        revWalk.dispose();
      }
    }

    CheckoutRulesRevWalk revWalk = null;
    try {
      revWalk = new CheckoutRulesRevWalk(myConfig, context, rules);
      return computeResult(startRevision, stopRevisions, visited, gitRoot, revWalk);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      if (revWalk != null) {
        revWalk.close();
        revWalk.dispose();
      }
    }
  }

  private boolean hasCommitsAffectingPaths(@NotNull final String startRevision,
                                           @NotNull final Set<String> excludedRevisions,
                                           @NotNull final Collection<String> paths,
                                           @NotNull final OperationContext context,
                                           @NotNull final GitVcsRoot gitRoot) throws VcsException {

    // we do not want to pass too many paths to the command line otherwise it can become too big
    int maxPaths = TeamCityProperties.getInteger("teamcity.git.checkoutRulesRevision.maxPathsForLogCommand", 10);
    final ChangedPathsCommand cmd = myVcs.getGitRepoOperations().changedPathsCommand();

    for (String rev: excludedRevisions) {
      for (List<String> pathsPart: Lists.partition(new ArrayList<>(paths), maxPaths)) {
        if (!cmd.commitsByPaths(context.getRepository(), gitRoot, startRevision, Collections.singleton(rev), 1, pathsPart).isEmpty()) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  private static Set<String> getParentsOfStopRevisions(final @NotNull Collection<String> stopRevisions,
                                                       final @NotNull OperationContext context,
                                                       final @NotNull CheckoutRulesRevWalk revWalk) throws IOException, VcsException {
    final Map<String, Set<String>> parentsMap = revWalk.getParentsMap(context.getRepository(), stopRevisions);
    Set<String> stopRevisionsParents = new HashSet<>();
    for (Map.Entry<String, Set<String>> entry : parentsMap.entrySet()) {
      if (entry.getValue().isEmpty()) {
        // one of the stop revisions is the initial commit, we can't use git diff command in this case
        return Collections.emptySet();
      }
      stopRevisionsParents.addAll(entry.getValue());
    }
    return stopRevisionsParents;
  }

  @NotNull
  private Result computeResult(final @NotNull String startRevision,
                               final @NotNull Collection<String> stopRevisions,
                               final @Nullable Set<String> visited,
                               final @NotNull GitVcsRoot gitRoot,
                               final @NotNull CheckoutRulesRevWalk revWalk) throws IOException {
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

  public static class GitChangesCollectionResult {
    @NotNull
    private final List<ModificationData> myModificationDataList;
    @Nullable
    private final Map<String, String> myBranchRevisions;

    private static final GitChangesCollectionResult EMPTY = new GitChangesCollectionResult(Collections.emptyList(), null);

    public GitChangesCollectionResult(@NotNull List<ModificationData> modificationDataList, @Nullable Map<String, String> failedToFindTipBranches) {
      myModificationDataList = modificationDataList;
      myBranchRevisions = failedToFindTipBranches;
    }

    @NotNull
    public List<ModificationData> getModificationDataList() {
      return myModificationDataList;
    }

    @Nullable
    public Map<String, String> getBranchRevisions() {
      return myBranchRevisions;
    }

    public ChangesCollectionResult toChangesCollectionResult() {
      return ChangesCollectionResult.create(myModificationDataList)
                                    .setUpToDateState(myBranchRevisions == null ? Collections.emptyMap() : myBranchRevisions);
    }

    public static GitChangesCollectionResult empty() {
      return EMPTY;
    }
  }
}