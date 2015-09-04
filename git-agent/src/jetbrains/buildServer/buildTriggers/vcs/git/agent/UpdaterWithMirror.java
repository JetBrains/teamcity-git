/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author dmitry.neverov
 */
public class UpdaterWithMirror extends UpdaterImpl {

  private final static Logger LOG = Logger.getLogger(UpdaterWithMirror.class);

  public UpdaterWithMirror(@NotNull AgentPluginConfig pluginConfig,
                           @NotNull MirrorManager mirrorManager,
                           @NotNull SmartDirectoryCleaner directoryCleaner,
                           @NotNull GitFactory gitFactory,
                           @NotNull AgentRunningBuild build,
                           @NotNull VcsRoot root,
                           @NotNull String version,
                           @NotNull File targetDir,
                           @NotNull CheckoutRules rules,
                           @NotNull CheckoutMode mode) throws VcsException {
    super(pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, mode);
  }

  @Override
  protected void doUpdate() throws VcsException {
    updateLocalMirror();
    super.doUpdate();
  }

  private void updateLocalMirror() throws VcsException {
    File bareRepositoryDir = myRoot.getRepositoryDir();
    String mirrorDescription = "local mirror of root " + myRoot.getName() + " at " + bareRepositoryDir;
    LOG.info("Update " + mirrorDescription);
    boolean fetchRequired = true;
    if (!isValidGitRepo(bareRepositoryDir))
      FileUtil.delete(bareRepositoryDir);
    boolean newMirror = false;
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(bareRepositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(myRoot.getRepositoryFetchURL().toString()).call();
      newMirror = true;
    } else {
      boolean outdatedTagsFound = removeOutdatedRefs(bareRepositoryDir);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision " + myRevision + " in " + mirrorDescription);
        Ref ref = getRef(bareRepositoryDir, GitUtils.expandRef(myRoot.getRef()));
        if (ref != null && myRevision.equals(ref.getObjectId().name())) {
          LOG.info("No fetch required for revision '" + myRevision + "' in " + mirrorDescription);
          fetchRequired = false;
        }
      }
    }
    Ref ref = getRef(bareRepositoryDir, myFullBranchName);
    if (ref == null)
      fetchRequired = true;
    if (!fetchRequired)
      return;
    if (!newMirror && optimizeMirrorBeforeFetch()) {
      GitFacade git = myGitFactory.create(bareRepositoryDir);
      git.gc().call();
      git.repack().call();
      removeOrphanedIdxFiles(bareRepositoryDir);
    }
    fetchMirror(bareRepositoryDir, "+" + myFullBranchName + ":" + GitUtils.expandRef(myFullBranchName), false);
    if (hasRevision(bareRepositoryDir, myRevision))
      return;
    fetchMirror(bareRepositoryDir, "+refs/heads/*:refs/heads/*", false);
  }


  private boolean optimizeMirrorBeforeFetch() {
    return "true".equals(myBuild.getSharedConfigParameters().get("teamcity.git.optimizeMirrorBeforeFetch"));
  }


  private void removeOrphanedIdxFiles(@NotNull File dotGitDir) {
    File packDir = new File(new File(dotGitDir, "objects"), "pack");
    File[] files = packDir.listFiles();
    if (files == null)
      return;

    Set<String> packs = new HashSet<String>();
    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".pack"))
        packs.add(name.substring(0, name.length() - 5));
    }

    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".idx")) {
        if (!packs.contains(name.substring(0, name.length() - 4)))
          FileUtil.delete(f);
      }
    }
  }


  private void fetchMirror(@NotNull File repositoryDir, @NotNull String refspec, boolean shallowClone) throws VcsException {
    removeRefLocks(repositoryDir);
    try {
      fetch(repositoryDir, refspec, shallowClone);
    } catch (VcsException e) {
      FileUtil.delete(repositoryDir);
      repositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(repositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(myRoot.getRepositoryFetchURL().toString()).call();
      fetch(repositoryDir, refspec, shallowClone);
    }
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

  private void setUseLocalMirror() throws VcsException {
    String remoteUrl = myRoot.getRepositoryFetchURL().toString();
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
    Map<String, Ref> existingRefs = myGitFactory.create(repositoryDir).showRef().call();
    int i = 0;
    while (existingRefs.containsKey("refs/heads/" + branchName)) {
      branchName = tmpBranchName + i;
      i++;
    }
    return branchName;
  }
}
