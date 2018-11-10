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
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LsTreeResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitExecTimeout;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.getGitDir;

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
      updateLocalMirror(true, myRoot.getRepositoryDir(), myRoot.getRepositoryFetchURL(), myFullBranchName, myRevision);
      //prepare refs for copying into working dir repository
      myGitFactory.create(myRoot.getRepositoryDir()).packRefs().call();
    } finally {
      myLogger.activityFinished(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    }
  }

  private void updateLocalMirror(boolean repeatFetchAttempt,
                                 File bareRepositoryDir,
                                 URIish fetchUrl,
                                 String branchname,
                                 String... revisions) throws VcsException {
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
      configureRemoteUrl(bareRepositoryDir, fetchUrl);
      mySSLInvestigator.setCertificateOptions(git);
      newMirror = true;
    } else {
      configureRemoteUrl(bareRepositoryDir, fetchUrl);
      mySSLInvestigator.setCertificateOptions(git);
      boolean outdatedRefsFound = removeOutdatedRefs(bareRepositoryDir);
      if (!outdatedRefsFound) {
        for (String revision : revisions) {
          LOG.debug("Try to find revision " + revision + " in " + mirrorDescription);
          Ref ref = getRef(bareRepositoryDir, GitUtils.createRemoteRef(branchname));
          if (ref != null && revision.equals(ref.getObjectId().name())) {
            LOG.info("No fetch required for revision '" + revision + "' in " + mirrorDescription);
            fetchRequired = false;
          }
        }
      }
    }
    FetchHeadsMode fetchHeadsMode = myPluginConfig.getFetchHeadsMode();
    Ref ref = getRef(bareRepositoryDir, branchname);
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
        fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+refs/heads/*:refs/heads/*", branchname, revisions);
        if (!myFullBranchName.startsWith("refs/heads/") && !hasRevisions(bareRepositoryDir, revisions))
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+" + branchname + ":" + GitUtils.expandRef(branchname), branchname, revisions);
        break;
      case BEFORE_BUILD_BRANCH:
        fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+refs/heads/*:refs/heads/*", branchname, revisions);
        if (!myFullBranchName.startsWith("refs/heads/") && !hasRevisions(bareRepositoryDir, revisions))
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+" + branchname + ":" + GitUtils.expandRef(branchname), branchname, revisions);
        break;
      case AFTER_BUILD_BRANCH:
        if (!hasRevisions(bareRepositoryDir, revisions)) {
          fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+" + branchname + ":" + GitUtils.expandRef(branchname), branchname, revisions);
          if (!hasRevisions(bareRepositoryDir, revisions)) {
            fetchMirror(repeatFetchAttempt, bareRepositoryDir, fetchUrl, "+refs/heads/*:refs/heads/*", branchname, revisions);
          }
        }
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
                           @NotNull URIish fetchUrl,
                           @NotNull String refspec,
                           @NotNull String branchname,
                           @NotNull String... revisions) throws VcsException {
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
        configureRemoteUrl(repositoryDir, fetchUrl);
        mySSLInvestigator.setCertificateOptions(git);
        fetch(repositoryDir, refspec, false);
      } else {
        LOG.info("Failed to delete repository " + repositoryDir + " after failed checkout, clone repository in another directory");
        myMirrorManager.invalidate(repositoryDir);
        updateLocalMirror(false, repositoryDir, fetchUrl, branchname, revisions);
      }
    }
  }


  private boolean shouldFetchFromScratch(@NotNull VcsException e) {
    if (e instanceof GitExecTimeout)
      return false;
    String msg = e.getMessage().toLowerCase();
    return !msg.contains("couldn't find remote ref") &&
           !msg.contains("could not read from remote repository");
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
      if (GitUtilsAgent.isTag(myFullBranchName)) {
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
    String localMirrorUrl = getLocalMirrorUrl(myRoot.getRepositoryDir());
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

  private String getLocalMirrorUrl(File repositoryDir) throws VcsException {
    try {
      // Cloning from local git repos is faster when using the absolute path, than when using the 'file://' syntax
      // See `man git clone` --local flag
      return new URIish(repositoryDir.getAbsolutePath()).toString();
    } catch (URISyntaxException e) {
      throw new VcsException("Cannot create uri for local mirror " + repositoryDir.getAbsolutePath(), e);
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

  @Override
  protected void updateSubmodules(@NotNull final File repositoryDir) throws VcsException, ConfigInvalidException, IOException {
    GitFacade git = myGitFactory.create(repositoryDir);

    Repository r = new RepositoryBuilder().setBare().setGitDir(getGitDir(repositoryDir)).build();
    StoredConfig gitConfig = r.getConfig();

    Map<String, AggregatedSubmodule> aggregatedSubmodules = new HashMap<String, AggregatedSubmodule>();

    String revision = git.revParse().setRef("HEAD").call();
    if (!StringUtil.isEmpty(revision)) {
      File dotGitModules = new File(repositoryDir, ".gitmodules");
      Config gitModules = readGitModules(dotGitModules);
      if (gitModules == null)
        return;

      Set<String> submodules = gitModules.getSubsections("submodule");
      for (String submoduleName : submodules) {
        String url = gitConfig.getString("submodule", submoduleName, "url");
        if (url == null) {
          Loggers.VCS.info(".git/config doesn't contain an url for submodule '" + submoduleName + "', use url from .gitmodules");
          url = gitModules.getString("submodule", submoduleName, "url");
        }
        if (StringUtil.isEmpty(url)) { // shouldn't happen unless .gitmodules is malformed & missing a url
          Loggers.VCS.warn("Could not determine submodule url for '" + submoduleName + "'");
          continue;
        }

        String submodulePath = gitModules.getString("submodule", submoduleName, "path");
        if (StringUtil.isEmpty(submodulePath)) { // // shouldn't happen unless .gitmodules is malformed & missing a path
          Loggers.VCS.warn("Could not determine submodule path for '" + submoduleName + "'");
          continue;
        }

        String submoduleRevision = getSubmoduleRevision(git, revision, submodulePath);
        if (StringUtil.isEmpty(submoduleRevision)) { // submodule path specified in .gitmodules may not actually exist
          Loggers.VCS.warn("Could not determine submodule commit for '" + submoduleName + "', at path '" + submodulePath + "'");
          continue;
        }

        // Build a map of submodule url -> (names, paths, commits)
        // The same submodule url can be checked out to multiple paths & at different commits, but we only need one local mirror.
        AggregatedSubmodule aggregatedSubmodule;
        if (aggregatedSubmodules.containsKey(url)) {
          aggregatedSubmodule = aggregatedSubmodules.get(url);
        } else {
          aggregatedSubmodule = new AggregatedSubmodule(url);
        }

        aggregatedSubmodule.addSubmoduleName(submoduleName);
        aggregatedSubmodule.addSubmodulePath(submodulePath);
        aggregatedSubmodule.addSubmoduleRevision(submoduleRevision);

        aggregatedSubmodules.put(url, aggregatedSubmodule);
      }

      for (AggregatedSubmodule submodule : aggregatedSubmodules.values()) {
        File mirrorRepositoryDir = myMirrorManager.getMirrorDir(submodule.getUrl());

        if (!hasRevisions(mirrorRepositoryDir, submodule.getRevisions()))
          updateLocalMirror(true,
                  mirrorRepositoryDir,
                  myRoot.getAuthSettings().createAuthURI(submodule.getUrl()),
                  "refs/heads/*",
                  submodule.getRevisions());

        for (String name : submodule.getNames()) {
          // Change the submodule url so that `git submodule update` will clone/fetch from the local mirror directory
          setUseLocalSubmoduleMirror(r.getDirectory(), name, getLocalMirrorUrl(mirrorRepositoryDir));
        }
      }
    }

    super.updateSubmodules(repositoryDir);

    for (AggregatedSubmodule submodule : aggregatedSubmodules.values()) {
      for (String name : submodule.getNames()) {
        File submoduleDir = new File(r.getDirectory().toString() + File.separator + "modules" + File.separator + name);
        if (submoduleDir.exists()) {
          // Fix the submodule's origin url - it will be equal to a local mirror directory since it was cloned/fetched from there
          // However, this breaks relative submodules which need to be relative to their origin url, not the mirror directory
          setUseRemoteSubmoduleOrigin(submoduleDir, submodule.getUrl());
        }
      }
    }
  }

  private String getSubmoduleRevision(@NotNull GitFacade git, @NotNull String revision, @NotNull String path) throws VcsException {
    LsTreeResult lsTreeResult = git.lsTree().setRevision(revision).setPath(path).call();
    if (lsTreeResult == null) {
      return null;
    }
    return lsTreeResult.getObject();
  }

  private void setUseLocalSubmoduleMirror(@NotNull File repositoryDir, @NotNull String submoduleName, @NotNull String localMirrorUrl) throws VcsException {
    GitFacade git = myGitFactory.create(repositoryDir);
    git.setConfig()
            .setPropertyName("submodule." + submoduleName + ".url")
            .setValue(localMirrorUrl)
            .call();
  }

  private void setUseRemoteSubmoduleOrigin(@NotNull File repositoryDir, @NotNull String originUrl) throws VcsException {
    GitFacade git = myGitFactory.create(repositoryDir);
    git.setConfig()
      .setPropertyName("remote.origin.url")
      .setValue(originUrl)
      .call();
  }

  private boolean hasRevisions(@NotNull File repositoryDir, String... revisions) {
    for (String revision : revisions) {
      if (!hasRevision(repositoryDir, revision)) {
        return false;
      }
    }
    return true;
  }

  private static final class AggregatedSubmodule {
    private final String url;
    private final Set<String> names;
    private final Set<String> paths;
    private final Set<String> revisions;

    public AggregatedSubmodule(String url) {
      this.url = url;
      this.names = new HashSet<String>();
      this.paths = new HashSet<String>();
      this.revisions = new HashSet<String>();
    }

    public void addSubmoduleName(@NotNull String name) {
      this.names.add(name);
    }

    public void addSubmodulePath(@NotNull String path) {
      this.paths.add(path);
    }

    public void addSubmoduleRevision(@NotNull String revision) {
      this.revisions.add(revision);
    }

    public String getUrl() {
      return url;
    }

    public String[] getNames() {
      return names.toArray(new String[0]);
    }

    public String[] getPaths() {
      return paths.toArray(new String[0]);
    }

    public String[] getRevisions() {
      return revisions.toArray(new String[0]);
    }
  }
}
