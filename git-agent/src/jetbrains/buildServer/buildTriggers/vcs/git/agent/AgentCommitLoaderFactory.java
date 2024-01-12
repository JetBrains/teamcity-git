

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Refs;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitExecTimeout;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentCommitLoaderFactory {

  public static final String REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED = "server does not allow request for unadvertised object";
  private static final int SILENT_TIMEOUT = 24 * 60 * 60; //24 hours
  /** Git version which supports --progress option in the fetch command */
  private static final GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);

  @NotNull private static AgentCommitLoader getCommitLoaderForMirror(@NotNull GitVcsRoot root,
                                                                    @NotNull AgentRunningBuild build,
                                                                    @NotNull File targetDirectory,
                                                                    @NotNull GitFactory gitFactory,
                                                                    @NotNull AgentPluginConfig pluginConfig,
                                                                    @NotNull String maxRefSpec,
                                                                    @NotNull BuildProgressLogger logger) {
    return new AbstractAgentCommitLoader(root, targetDirectory, gitFactory, pluginConfig, maxRefSpec, logger) {
      @NotNull
      @Override
      protected String getRemoteRefName(@NotNull String branch) {
        return GitUtils.expandRef(branch);
      }

      @NotNull
      @Override
      protected File getGitDir() {
        return targetDirectory;
      }

      @Override
      protected void beforeFetch() throws VcsException {
        if (optimizeMirrorBeforeFetch()) {
          final AgentGitFacade git = gitFactory.create(targetDirectory);
          git.gc().call();
          git.repack().call();
        }
      }

      private boolean optimizeMirrorBeforeFetch() {
        return "true".equals(build.getSharedConfigParameters().get("teamcity.git.optimizeMirrorBeforeFetch"));
      }
    };
  }

  @NotNull public static AgentCommitLoader getCommitLoader(@NotNull GitVcsRoot root,
                                                           @NotNull File targetDirectory,
                                                           @NotNull GitFactory gitFactory,
                                                           @NotNull AgentPluginConfig pluginConfig,
                                                           @NotNull BuildProgressLogger logger) {
    return new AbstractAgentCommitLoader(root, targetDirectory, gitFactory, pluginConfig, "+refs/heads/*:refs/remotes/origin/*", logger) {
      @NotNull
      @Override
      protected String getRemoteRefName(@NotNull String branch) {
        return GitUtils.createRemoteRef(branch);
      }

      @NotNull
      @Override
      protected File getGitDir() {
        return new File(targetDirectory, ".git");
      }

      @Override
      protected void beforeFetch() {

      }
    };
  }

  @NotNull public static AgentCommitLoader getCommitLoaderForMirror(@NotNull GitVcsRoot root,
                                                                    @NotNull AgentRunningBuild build,
                                                                    @NotNull File targetDirectory,
                                                                    @NotNull GitFactory gitFactory,
                                                                    @NotNull AgentPluginConfig pluginConfig,
                                                                    @NotNull BuildProgressLogger logger) {
    return getCommitLoaderForMirror(root, build, targetDirectory, gitFactory, pluginConfig, "+refs/heads/*:refs/heads/*", logger);
  }


  @NotNull
  public static AgentCommitLoader getCommitLoaderForSubmodule(@NotNull GitVcsRoot root,
                                                              @NotNull AgentRunningBuild build,
                                                              @NotNull File targetDirectory,
                                                              @NotNull GitFactory gitFactory,
                                                              @NotNull AgentPluginConfig pluginConfig,
                                                              @NotNull BuildProgressLogger logger) {

    return getCommitLoaderForMirror(root, build, targetDirectory, gitFactory, pluginConfig, "+refs/*:refs/*", logger);
  }

  private static abstract class AbstractAgentCommitLoader implements AgentCommitLoader {

    @NotNull private final GitVcsRoot myRoot;
    @NotNull private final File myTargetDirectory;

    @NotNull private final GitFactory myGitFactory;
    @NotNull private final AgentPluginConfig myPluginConfig;

    @NotNull private final String myMaxRefSpec;

    @NotNull private final BuildProgressLogger myLogger;

    public AbstractAgentCommitLoader(@NotNull GitVcsRoot root,
                                     @NotNull File targetDirectory,
                                     @NotNull GitFactory gitFactory,
                                     @NotNull AgentPluginConfig pluginConfig,
                                     @NotNull String maxRefSpec,
                                     @NotNull BuildProgressLogger logger) {
      myRoot = root;
      myTargetDirectory = targetDirectory;
      myGitFactory = gitFactory;
      myPluginConfig = pluginConfig;
      myMaxRefSpec = maxRefSpec;
      myLogger = logger;
    }

    @Override
    public boolean loadCommit(@NotNull String sha) throws VcsException {
      if (hasRevision(sha)) return true;
      fetchAllBranches();
      return hasRevision(sha);
    }

    @Override
    public boolean loadCommitInBranch(@NotNull String sha, @NotNull String branch, boolean enforceFetch) throws VcsException {
      final FetchHeadsMode fetchHeadsMode = myPluginConfig.getFetchHeadsMode();
      switch (fetchHeadsMode) {
        case ALWAYS:
          myLogger.message(getForcedHeadsFetchMessage());

          beforeFetch();
          fetchAllBranches();
          if (isSingleBranchFetchRequired(branch) && isFetchRequired(sha, branch)) {
            fetchBranch(branch);
          }
          break;

        case BEFORE_BUILD_BRANCH:
          if (!isFetchRequired(sha, branch, enforceFetch)) return true;

          beforeFetch();
          fetchAllBranches();
          if (isSingleBranchFetchRequired(branch) && isFetchRequired(sha, branch)) {
            fetchBranch(branch);
          }
          break;

        case AFTER_BUILD_BRANCH:
          if (!isFetchRequired(sha, branch, enforceFetch)) return true;

          beforeFetch();
          fetchBranch(branch);
          if (hasRevision(sha)){
            return true;
          }
          fetchAllBranches();
          break;

        default:
          throw new VcsException("Unknown FetchHeadsMode: " + fetchHeadsMode);
      }

      return hasRevision(sha);
    }

    private boolean isFetchRequired(@NotNull String sha, @NotNull String branch, boolean enforceFetch) {
      if (enforceFetch) {
        myLogger.message("Local clone state requires 'git fetch'.");
        return true;
      }
      return isFetchRequired(sha, branch);
    }

    private boolean isFetchRequired(@NotNull String sha, @NotNull String branch) {
      final String remoteRefName = getRemoteRefName(branch);
      final Ref remoteRef = getRef(remoteRefName);
      if (remoteRef == null) {
        message("'git fetch' required: '" + remoteRefName + "' is not found in the local repository clone.");
        return true;
      }
      if (hasRevision(sha)) {
        if (myPluginConfig.isNoFetchRequiredIfRevisionInRepo()) return false;

        final ObjectId remoteRefObject = remoteRef.getObjectId();
        if (remoteRefObject == null) {
          message("'git fetch' required: commit '" + sha + "' is in the local repository clone, but '" + remoteRefName + "' points to no commit.");
          return true;
        }
        final String remoteRefSha = remoteRefObject.name();
        if (sha.equals(remoteRefSha)) {
          message("No 'git fetch' required: commit '" + sha + "' is in the local repository clone pointed by '" + remoteRefName + "'.");
          return false;
        }
        message("'git fetch' required: commit '" + sha + "' is in the local repository clone, but '" + remoteRefName + "' points to another commit '" + remoteRefSha + "'.");
        return true;
      }
      message("'git fetch' required: commit '" + sha + "' is not found in the local repository clone.");
      return true;
    }

    @Override
    public boolean loadCommitPreferShallow(@NotNull String sha, @NotNull String branch) throws VcsException {
      final FetchHeadsMode fetchHeadsMode = myPluginConfig.getFetchHeadsMode();
      if (fetchHeadsMode == FetchHeadsMode.AFTER_BUILD_BRANCH) {
        if (hasRevision(sha) && hasBranch(branch)) {
          myLogger.debug("Branch '" + branch + "' and revision '" + sha + "' are present in the local repository, skip fetch");
          return true;
        }

        final boolean branchPointsTheRevision = isRemoteBranchPointsTheRevision(branch, sha);
        if (GitUtilsAgent.isTag(branch) && branchPointsTheRevision) {
          fetchBranch(branch, true);
        } else {
          try {
            fetch(myTargetDirectory, getRefSpecForRevision(sha, branch), true);
          } catch (VcsException e) {
            if (isRequestNotAllowed(e)) {
              myLogger.warning(StringUtil.capitalize(REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED) + ": to speed-up the checkout configure your remote repository to allow directly fetching commits (set uploadpack.allowReachableSHA1InWant or uploadpack.allowAnySHA1InWant config variables to true in the remote git config)");

              if (branchPointsTheRevision) {
                fetchBranch(branch, true);
              }
            } else throw e;
          }
        }

        if (hasRevision(sha)) {
          return true;
        }

        myLogger.debug("Failed to get the revision '" + sha + "' using shallow fetch, will try regular fetch");
      } else {
        myLogger.warning("Shallow fetch won't be performed because " + PluginConfigImpl.FETCH_ALL_HEADS + " parameter is set to " + myPluginConfig.getFetchAllHeadsModeStr() + ", which is incompatible with shallow clone.");
      }

      return loadCommitInBranch(sha, branch, false);
    }

    @Override
    public boolean loadShallowBranch(@NotNull String sha, @NotNull String branch) throws VcsException {
      File mirrorRepositoryDir = myRoot.getRepositoryDir();
      if (GitUtilsAgent.isTag(branch)) {
        //handle tags specially: if we fetch a temporary branch which points to a commit
        //tags points to, git fetches both branch and tag, tries to make a local
        //branch to track both of them and fails.
        String refspec = "+" + branch + ":" + branch;
        fetch(myTargetDirectory, refspec, true);
      } else {
        String tmpBranchName = createTmpBranch(mirrorRepositoryDir, sha);
        String tmpBranchRef = "refs/heads/" + tmpBranchName;
        String refspec = "+" + tmpBranchRef + ":" + GitUtils.createRemoteRef(branch);
        fetch(myTargetDirectory, refspec, true);
        myGitFactory.create(mirrorRepositoryDir).deleteBranch().setName(tmpBranchName).call();
      }
      return true; // always return true, return value never used, calling code is anyway incorrect
    }

    @NotNull protected abstract String getRemoteRefName(@NotNull String branch);

    @NotNull protected abstract File getGitDir();

    protected abstract void beforeFetch() throws VcsException;

    private boolean isSingleBranchFetchRequired(@NotNull String branch) {
      return !branch.startsWith("refs/heads");
    }

    @Nullable
    protected Ref getRef(@NotNull String ref) {
      Map<String, Ref> refs = myGitFactory.create(myTargetDirectory).showRef().setPattern(ref).call().getValidRefs();
      return refs.isEmpty() ? null : refs.get(ref);
    }

    private void fetchBranch(@NotNull String branch) throws VcsException {
      fetchBranch(branch, false);
    }

    private void fetchBranch(@NotNull String branch, boolean shallow) throws VcsException {
      fetch(myTargetDirectory, getRefspecForFetch(branch), shallow);
    }

    @NotNull
    private String getRefspecForFetch(@NotNull String branch) {
      return "+" + branch + ":" + getRemoteRefName(branch);
    }

    @NotNull
    private String getRefSpecForRevision(@NotNull String sha, @NotNull String branch) {
      if (GitUtilsAgent.isTag(branch)) {
        return sha;
      }
      return "+" + sha + ":" + getRemoteRefName(branch);
    }

    private void fetchAllBranches() throws VcsException {
      fetch(myTargetDirectory, myMaxRefSpec, false);
    }

    protected boolean hasRevision(@NotNull String revision) {
      return getRevision(myTargetDirectory, revision) != null;
    }

    protected boolean hasBranch(@NotNull String branch) {
      return myGitFactory.create(myTargetDirectory)
                         .showRef()
                         .setPattern(branch)
                         .call()
                         .getValidRefs()
                         .size() > 0;
    }

    private String getRevision(@NotNull File repositoryDir, @NotNull String revision) {
      return myGitFactory.create(repositoryDir).log()
                         .setCommitsNumber(1)
                         .setPrettyFormat("%H%x20%s")
                         .setStartPoint(revision)
                         .call();
    }

    private void fetch(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone) throws VcsException {
      boolean silent = isSilentFetch();
      int timeout = getTimeout(silent);

      try {
        callFetchWithRetry(repositoryDir, refspec, shallowClone, silent, timeout);
      } catch (GitIndexCorruptedException e) {
        File gitIndex = e.getGitIndex();
        myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat git fetch");
        FileUtil.delete(gitIndex);
        callFetchWithRetry(repositoryDir, refspec, shallowClone, silent, timeout);
      } catch (GitExecTimeout e) {
        if (!silent) {
          myLogger.error("No output from git during " + timeout + " seconds. Try increasing idle timeout by setting parameter '"
                         + PluginConfigImpl.IDLE_TIMEOUT +
                         "' either in build or in agent configuration.");
        }
        throw e;
      }
    }


    private void callFetchWithRetry(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone, boolean silent, int timeout) throws VcsException {
      final FetchCommand result = myGitFactory.create(repositoryDir).fetch()
                                              .setAuthSettings(myRoot.getAuthSettings())
                                              .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
                                              .setTimeout(timeout)
                                              .setRefspec(refspec)
                                              .setFetchTags(myPluginConfig.isFetchTags())
                                              .setRetryAttempts(myPluginConfig.getRemoteOperationAttempts())
                                              .trace(myPluginConfig.getGitTraceEnv())
                                              .addPreAction(() -> GitUtils.removeRefLocks(getGitDir()));

      if (silent)
        result.setQuite(true);
      else
        result.setShowProgress(true);

      if (shallowClone)
        result.setDepth(1);

      result.call();
    }

    private boolean isSilentFetch() {
      GitVersion version = myPluginConfig.getGitVersion();
      return version.isLessThan(GIT_WITH_PROGRESS_VERSION);
    }

    private int getTimeout(boolean silentFetch) {
      if (silentFetch)
        return SILENT_TIMEOUT;
      else
        return myPluginConfig.getIdleTimeoutSeconds();
    }

    private String getForcedHeadsFetchMessage() {
      return "Forced fetch (" + PluginConfigImpl.FETCH_ALL_HEADS + "=" + myPluginConfig.getFetchAllHeadsModeStr() + ") with " + myMaxRefSpec +" refspec";
    }

    private boolean isRemoteBranchPointsTheRevision(@NotNull String branch, @NotNull String sha) throws VcsException {
      return getRemoteRefs(myTargetDirectory).list().stream().anyMatch(r -> branch.equals(r.getName()) && sha.equals(r.getObjectId().getName()));
    }

    private boolean isRequestNotAllowed(@NotNull VcsException e) {
      final String msg = e.getMessage();
      return msg != null && msg.toLowerCase().contains(REQUEST_UNADVERTISED_OBJECT_NOT_ALLOWED);
    }

    private String createTmpBranch(@NotNull File repositoryDir, @NotNull String branchStartingPoint) throws VcsException {
      String tmpBranchName = getUnusedBranchName(repositoryDir);
      myGitFactory.create(repositoryDir)
                  .createBranch()
                  .setName(tmpBranchName)
                  .setStartPoint(branchStartingPoint)
                  .call();
      return tmpBranchName;
    }

    private String getUnusedBranchName(@NotNull File repositoryDir) {
      final String tmpBranchName = "tmp_branch_for_build";
      String branchName = tmpBranchName;
      Map<String, Ref> existingRefs = myGitFactory.create(repositoryDir).showRef().call().getValidRefs();
      int i = 0;
      while (existingRefs.containsKey("refs/heads/" + branchName)) {
        branchName = tmpBranchName + i;
        i++;
      }
      return branchName;
    }

    @NotNull
    protected Refs getRemoteRefs(@NotNull File workingDir) throws VcsException {
      return new Refs(myGitFactory.create(workingDir).lsRemote().setAuthSettings(myRoot.getAuthSettings())
                                  .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
                                  .setTimeout(myPluginConfig.getLsRemoteTimeoutSeconds())
                                  .setRetryAttempts(myPluginConfig.getRemoteOperationAttempts())
                                  .trace(myPluginConfig.getGitTraceEnv())
                                  .call());
    }
    protected void message(@NotNull String msg) {
      myLogger.message(msg);
    }
  }
}