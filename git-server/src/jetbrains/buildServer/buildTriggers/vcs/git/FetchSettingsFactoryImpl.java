package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;
import static jetbrains.buildServer.buildTriggers.vcs.git.CommitLoaderImpl.LOG;

public class FetchSettingsFactoryImpl implements FetchSettingsFactory {

  private static final String REF_MISSING_FORMAT = "Ref %s is no longer present in the remote repository";
  private static final String REFS_MISSING_FORMAT = "Refs %s are no longer present in the remote repository";

  @NotNull
  @Override
  public FetchSettings getFetchSettings(@NotNull OperationContext context,
                                        @NotNull Collection<RefCommit> refsToFetch,
                                        @NotNull Collection<RefCommit> revisions,
                                        @NotNull Set<String> remoteRefs,
                                        boolean includeTags) throws VcsException {
    final Set<String> filteredRemoteRefs = getFilteredRemoteRefs(context, remoteRefs);
    final boolean fetchAllRefs = shouldFetchRemoteRefs(context, revisions, filteredRemoteRefs);
    final Collection<RefSpec> refSpecs = getRefSpecForCurrentState(context, refsToFetch, remoteRefs);
    return getFetchSettings(context, refSpecs, fetchAllRefs, includeTags);
  }

  @NotNull
  @Override
  public FetchSettings getFetchSettings(@NotNull OperationContext context, boolean includeTags) throws VcsException {
    return getFetchSettings(context, getAllRefSpec(), true, includeTags);
  }

  private FetchSettings getFetchSettings(@NotNull OperationContext context, @NotNull Collection<RefSpec> refSpecs,
                                         boolean fetchAllRefs, boolean includeTags) throws VcsException {
    final FetchSettings settings = new FetchSettings(context.getGitRoot().getAuthSettings(), context.getProgress(), refSpecs);
    settings.setFetchMode(FetchSettings.getFetchMode(fetchAllRefs, includeTags));
    return settings;
  }

  private Set<String> getFilteredRemoteRefs(@NotNull OperationContext context, @NotNull Set<String> refs) throws VcsException {
    final GitVcsRoot gitRoot = context.getGitRoot();
    final boolean reportTags = gitRoot.isReportTags();
    if (reportTags) return refs;

    final String defaultBranch = GitUtils.expandRef(gitRoot.getRef());
    return refs.stream().filter(r -> !GitServerUtil.isTag(r) || defaultBranch.equals(r)).collect(Collectors.toSet());
  }


  private boolean shouldFetchRemoteRefs(@NotNull OperationContext context, @NotNull Collection<RefCommit> revisions, @NotNull Collection<String> filteredRemoteRefs) {
    final float factor = context.getPluginConfig().fetchRemoteBranchesFactor();
    if (factor == 0) return false;

    final int currentStateNum = revisions.stream().map(RefCommit::getRef).collect(Collectors.toSet()).size();
    if (currentStateNum == 1) return false;

    final int remoteNum = filteredRemoteRefs.size();
    return remoteNum < currentStateNum || (float)currentStateNum / remoteNum >= factor;
  }

  @NotNull
  private Collection<RefSpec> getRefSpecForCurrentState(@NotNull OperationContext context, @NotNull Collection<RefCommit> revisions, @NotNull Collection<String> remoteRefs) throws VcsException {
    final Set<RefSpec> result = new HashSet<>();
    final Set<String> missingTips = new HashSet<>();

    for (RefCommit r : revisions) {
      final String ref = r.getRef();
      final boolean existsRemotely = remoteRefs.contains(ref);
      if (existsRemotely) {
        result.add(new RefSpec(ref + ":" + ref).setForceUpdate(true));
        continue;
      }
      if (r.isRefTip()) {
        missingTips.add(ref);
      } else {
        LOG.debug(String.format(REF_MISSING_FORMAT, ref) + " for " + context.getGitRoot().debugInfo());
      }
    }

    if (TeamCityProperties.getBoolean("teamcity.git.failLoadCommitsIfRemoteBranchMissing")) {
      final int remotelyMissingRefsNum = missingTips.size();
      if (remotelyMissingRefsNum > 0) {
        final String message = remotelyMissingRefsNum == 1 ?
                               String.format(REF_MISSING_FORMAT, missingTips.iterator().next()) :
                               String.format(REFS_MISSING_FORMAT, StringUtil.join(", ", missingTips));

        final VcsException exception = new VcsException(message);
        exception.setRecoverable(context.getPluginConfig().treatMissingBranchTipAsRecoverableError());
        throw exception;
      }
    }
    return result;
  }

  @NotNull
  private static Set<RefSpec> getAllRefSpec() {
    return Collections.singleton(new RefSpec("refs/*:refs/*").setForceUpdate(true));
  }

}
