

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitApiClientFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitProxyChangesCollector;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitProxySettings;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.GitRepoApi;
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

public class GitCollectChangesPolicy implements CollectChangesBetweenRepositories, RevisionMatchedByCheckoutRulesCalculator {

  private static final String ENABLE_CHANGES_COLLECTION_LOGGING = "teamcity.internal.git.changesCollectionTimeLogging.enabled";
  private static final String ENABLE_GIT_PROXY_COMPARISON_LOGGING = "teamcity.internal.git.gitProxy.changesCollectionComparison.enabled";
  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());

  private final GitVcsSupport myVcs;
  private final VcsOperationProgressProvider myProgressProvider;
  private final ServerPluginConfig myConfig;
  private final RepositoryManager myRepositoryManager;
  private final CheckoutRulesLatestRevisionCache myCheckoutRulesLatestRevisionCache;
  private final Counter myCollectChangesMetric;
  private final Counter myComputeRevisionMetric;
  private final GitProxyChangesCollector myGitProxyChangesCollector;

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
    myGitProxyChangesCollector = new GitProxyChangesCollector(parameterFactory, gitApiClientFactory, repositoryManager);
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
    SProject project = retrieveProject(root);
    try (Stoppable stoppable = myCollectChangesMetric.startMsecsTimer()) {
      final GitProxySettings proxyCredentials = myGitProxyChangesCollector.getGitProxyInfo(root, project, "");
      if (proxyCredentials == null) {
        List<ModificationData> jgitResult = runCollectChangesWithTimer("jgit", root, project, false, () -> collectChangesJgit(root, fromState, toState));
        if (project != null && Boolean.parseBoolean(project.getParameterValue(ENABLE_GIT_PROXY_COMPARISON_LOGGING))) {
          try {
            GitProxySettings testProxyCredentials = myGitProxyChangesCollector.getGitProxyInfo(root, project, ".comparisonTest");
            if (testProxyCredentials != null) {
              List<ModificationData> gitProxyResult = runCollectChangesWithTimer("gitProxy", root, project, true,
                                                                                 () -> myGitProxyChangesCollector.collectChangesGitProxy(root, fromState, toState, testProxyCredentials));
              GitRepoApi api = myGitProxyChangesCollector.getClient(testProxyCredentials, root);
              myGitProxyChangesCollector.logAnyDifferences(jgitResult, gitProxyResult, fromState, toState, root, Objects.requireNonNull(api, "Git repo api can't be null"));
            }
          } catch (IgnoredCollectChangesFailure ignored) {
          } catch (Throwable t) {
            LOG.error("Failed to compare gitProxy and jgit changes collection results", t);
          }
        }
        return jgitResult;
      } else {
        return runCollectChangesWithTimer("gitProxy", root, project, false, () -> myGitProxyChangesCollector.collectChangesGitProxy(root, fromState, toState, proxyCredentials));
      }
    }
  }

  private static class IgnoredCollectChangesFailure extends RuntimeException { }

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
        throw new IgnoredCollectChangesFailure();
      }
    }

    logChangesCollectionOperationData(methodName, root, project, System.currentTimeMillis() - startTime, null);

    return result;
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
      OperationContext context = myVcs.createContext(root, "latest revision affecting checkout", createProgress());
      try {
        GitVcsRoot gitRoot = context.getGitRoot();
        return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
          Result finalResult = null;
          CheckoutRulesLatestRevisionCache.Value cached = myCheckoutRulesLatestRevisionCache.getCachedValue(gitRoot, startRevisionBranchName, rules);
          if (cached != null && cached.myStartRevision.equals(startRevision) && cached.myStopRevisions.equals(stopRevisions)) {
            return new Result(cached.myComputedRevision, cached.myReachedStopRevisions);
          }

          ensureRevisionIsFetched(startRevision, startRevisionBranchName, context);

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
        context.close();
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