/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class UpdaterWithMirror extends UpdaterImpl {

  private final static Logger LOG = Logger.getInstance(UpdaterWithMirror.class.getName());

  public UpdaterWithMirror(@NotNull FS fs,
                           @NotNull AgentPluginConfig pluginConfig,
                           @NotNull MirrorManager mirrorManager,
                           @NotNull SmartDirectoryCleaner directoryCleaner,
                           @NotNull GitFactory gitFactory,
                           @NotNull AgentRunningBuild build,
                           @NotNull VcsRoot root,
                           @NotNull String version,
                           @NotNull File targetDir,
                           @NotNull CheckoutRules rules,
                           @NotNull CheckoutMode mode) throws VcsException {
    super(fs, pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, mode);
  }

  @Override
  protected void doUpdate() throws VcsException {
    updateLocalMirror();
    super.doUpdate();
  }

  private void updateLocalMirror() throws VcsException {
    String message = "Update git mirror (" + myRoot.getRepositoryDir() + ")";
    myLogger.activityStarted(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    try {
      updateLocalMirror(true);
      //prepare refs for copying into working dir repository
      myGitFactory.create(myRoot.getRepositoryDir()).packRefs().call();
    } finally {
      myLogger.activityFinished(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    }
  }

  private void updateLocalMirror(boolean repeatFetchAttempt) throws VcsException {
    File bareRepositoryDir = myRoot.getRepositoryDir();
    String mirrorDescription = "local mirror of root " + myRoot.getName() + " at " + bareRepositoryDir;
    LOG.info("Update " + mirrorDescription);
    boolean fetchRequired = true;
    if (isValidGitRepo(bareRepositoryDir)) {
      removeOrphanedIdxFiles(bareRepositoryDir);
    } else {
      FileUtil.delete(bareRepositoryDir);
    }
    final GitFacade git = myGitFactory.create(bareRepositoryDir);
    boolean newMirror = false;
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      git.init().setBare(true).call();
      configureRemoteUrl(bareRepositoryDir);
      mySSLInvestigator.setCertificateOptions(git);
      newMirror = true;
    } else {
      configureRemoteUrl(bareRepositoryDir);
      mySSLInvestigator.setCertificateOptions(git);
      boolean outdatedTagsFound = removeOutdatedRefs(bareRepositoryDir);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision " + myRevision + " in " + mirrorDescription);
        Ref ref = getRef(bareRepositoryDir, GitUtils.createRemoteRef(myFullBranchName));
        if (ref != null && myRevision.equals(ref.getObjectId().name())) {
          LOG.info("No fetch required for revision '" + myRevision + "' in " + mirrorDescription);
          fetchRequired = false;
        }
      }
    }
    FetchHeadsMode fetchHeadsMode = myPluginConfig.getFetchHeadsMode();
    Ref ref = getRef(bareRepositoryDir, myFullBranchName);
    if (ref == null)
      fetchRequired = true;
    if (!fetchRequired && fetchHeadsMode != FetchHeadsMode.ALWAYS)
      return;
    if (!newMirror && optimizeMirrorBeforeFetch()) {
      git.gc().call();
      git.repack().call();
    }

    switch (fetchHeadsMode) {
      case ALWAYS:
        String msg = getForcedHeadsFetchMessage();
        LOG.info(msg);
        myLogger.message(msg);
        fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+refs/heads/*:refs/heads/*");
        if (!myFullBranchName.startsWith("refs/heads/") && !hasRevision(bareRepositoryDir, myRevision))
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+" + myFullBranchName + ":" + GitUtils.expandRef(myFullBranchName));
        break;
      case BEFORE_BUILD_BRANCH:
        fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+refs/heads/*:refs/heads/*");
        if (!myFullBranchName.startsWith("refs/heads/") && !hasRevision(bareRepositoryDir, myRevision))
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+" + myFullBranchName + ":" + GitUtils.expandRef(myFullBranchName));
        break;
      case AFTER_BUILD_BRANCH:
        fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+" + myFullBranchName + ":" + GitUtils.expandRef(myFullBranchName));
        if (!hasRevision(bareRepositoryDir, myRevision))
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, "+refs/heads/*:refs/heads/*");
        break;
      default:
        throw new VcsException("Unknown FetchHeadsMode: " + fetchHeadsMode);
    }
  }


  private boolean optimizeMirrorBeforeFetch() {
    return "true".equals(myBuild.getSharedConfigParameters().get("teamcity.git.optimizeMirrorBeforeFetch"));
  }


  private void fetchMirror(boolean repeatFetchAttempt,
                           @NotNull File repositoryDir,
                           @NotNull String refspec) throws VcsException {
    removeRefLocks(repositoryDir);
    try {
      final int[] retryTimeouts = getRetryTimeouts();
      for (int i = 0; i <= retryTimeouts.length; i++) {
        try {
          fetch(repositoryDir, refspec, false);
          break;
        } catch (GitExecTimeout e) {
          throw e;
        } catch (VcsException e) {
          if (!repeatFetchAttempt) throw e;
          // Throw exception after latest attempt
          if (i == retryTimeouts.length) throw e;
          int wait = retryTimeouts[i];
          LOG.warnAndDebugDetails("Failed to fetch mirror, will retry after " + wait + " seconds.", e);
          try {
            Thread.sleep(wait * 1000);
          } catch (InterruptedException e1) {
            throw new VcsException("Failed to fetch mirror", e1);
          }
        }
      }
    } catch (VcsException e) {
      if (myPluginConfig.isFailOnCleanCheckout() || !repeatFetchAttempt || !shouldFetchFromScratch(e))
        throw e;
      LOG.warnAndDebugDetails("Failed to fetch mirror", e);
      if (cleanDir(repositoryDir)) {
        GitFacade git = myGitFactory.create(repositoryDir);
        git.init().setBare(true).call();
        configureRemoteUrl(repositoryDir);
        mySSLInvestigator.setCertificateOptions(git);
        fetch(repositoryDir, refspec, false);
      } else {
        LOG.info("Failed to delete repository " + repositoryDir + " after failed checkout, clone repository in another directory");
        myMirrorManager.invalidate(repositoryDir);
        updateLocalMirror(false);
      }
    }
  }


  private boolean shouldFetchFromScratch(@NotNull VcsException e) {
    if (e instanceof GitExecTimeout)
      return false;
    String msg = e.getMessage();
    if (msg.contains("Couldn't find remote ref") ||
        msg.contains("Could not read from remote repository")) {
      return false;
    }
    return true;
  }


  private boolean cleanDir(final @NotNull File repositoryDir) {
    return myFS.delete(repositoryDir) && myFS.mkdirs(repositoryDir);
  }


  private boolean isValidGitRepo(@NotNull File gitDir) {
    try {
      new RepositoryBuilder().setGitDir(gitDir).setMustExist(true).build();
      return true;
    } catch (IOException e) {
      return false;
    }
  }


  @Override
  protected void setupExistingRepository() throws VcsException {
    removeUrlSections();
    setUseLocalMirror();
    disableAlternates();
  }

  @Override
  protected void setupNewRepository() throws VcsException {
    setUseLocalMirror();
    disableAlternates();
  }

  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    if (myPluginConfig.isUseShallowClone()) {
      File mirrorRepositoryDir = myRoot.getRepositoryDir();
      if (GitUtils.isTag(myFullBranchName)) {
        //handle tags specially: if we fetch a temporary branch which points to a commit
        //tags points to, git fetches both branch and tag, tries to make a local
        //branch to track both of them and fails.
        String refspec = "+" + myFullBranchName + ":" + myFullBranchName;
        fetch(myTargetDirectory, refspec, true);
      } else {
        String tmpBranchName = createTmpBranch(mirrorRepositoryDir, myRevision);
        String tmpBranchRef = "refs/heads/" + tmpBranchName;
        String refspec = "+" + tmpBranchRef + ":" + GitUtils.createRemoteRef(myFullBranchName);
        fetch(myTargetDirectory, refspec, true);
        myGitFactory.create(mirrorRepositoryDir).deleteBranch().setName(tmpBranchName).call();
      }
    } else {
      super.ensureCommitLoaded(fetchRequired);
    }
  }


  @NotNull
  private String readRemoteUrl() throws VcsException {
    Repository repository = null;
    try {
      repository = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      return repository.getConfig().getString("remote", "origin", "url");
    } catch (IOException e) {
      throw new VcsException("Error while reading remote repository url", e);
    } finally {
      if (repository != null)
        repository.close();
    }
  }


  private void setUseLocalMirror() throws VcsException {
    //read remote url from config instead of VCS root, they can be different
    //e.g. due to username exclusion from http(s) urls
    String remoteUrl = readRemoteUrl();
    String localMirrorUrl = getLocalMirrorUrl();
    GitFacade git = myGitFactory.create(myTargetDirectory);
    git.setConfig()
      .setPropertyName("url." + localMirrorUrl + ".insteadOf")
      .setValue(remoteUrl)
      .call();
    git.setConfig()
      .setPropertyName("url." + remoteUrl + ".pushInsteadOf")
      .setValue(remoteUrl)
      .call();
  }

  private String getLocalMirrorUrl() throws VcsException {
    try {
      return new URIish(myRoot.getRepositoryDir().toURI().toASCIIString()).toString();
    } catch (URISyntaxException e) {
      throw new VcsException("Cannot create uri for local mirror " + myRoot.getRepositoryDir().getAbsolutePath(), e);
    }
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

  private int[] getRetryTimeouts() {
    String value = myBuild.getSharedConfigParameters().get("teamcity.git.fetchMirrorRetryTimeouts");
    if (value == null) return new int[]{5, 10, 15, 30}; // total 60 seconds

    List<String> split = StringUtil.split(value, true, ',');
    int[] result = new int[split.size()];
    for (int i = 0; i < result.length; i++) {
      int parsed = 1;
      try {
        parsed = Integer.parseInt(split.get(i));
      } catch (NumberFormatException ignored) {
      }
      result[i] = parsed;
    }
    return result;
  }
}
