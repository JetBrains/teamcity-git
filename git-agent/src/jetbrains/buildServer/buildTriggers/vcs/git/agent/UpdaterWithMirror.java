/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;

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
                           @NotNull File targetDir) throws VcsException {
    super(pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir);
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
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(bareRepositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(myRoot.getRepositoryFetchURL().toString()).call();
    } else {
      boolean outdatedTagsFound = removeOutdatedTags(bareRepositoryDir);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision " + myRevision + " in " + mirrorDescription);
        Ref ref = getRef(bareRepositoryDir, GitUtils.expandRef(myRoot.getRef()));
        if (ref != null && myRevision.equals(ref.getObjectId().name())) {
          LOG.info("No fetch required for revision '" + myRevision + "' in " + mirrorDescription);
          fetchRequired = false;
        }
      }
    }
    if (fetchRequired)
      fetch(bareRepositoryDir, "+" + myFullBranchName + ":" + GitUtils.expandRef(myFullBranchName), false);
    if (hasRevision(bareRepositoryDir, myRevision))
      return;
    fetch(bareRepositoryDir, "+refs/heads/*:refs/heads/*", false);
  }


  @Override
  protected void setupMirrors() throws VcsException {
    if (!isRepositoryUseLocalMirror())
      setUseLocalMirror();
  }

  @Override
  protected void postInit() throws VcsException {
    setUseLocalMirror();
  }

  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    if (myPluginConfig.isUseShallowClone()) {
      File mirrorRepositoryDir = myRoot.getRepositoryDir();
      String tmpBranchName = createTmpBranch(mirrorRepositoryDir, myRevision);
      String tmpBranchRef = "refs/heads/" + tmpBranchName;
      String refspec = "+" + tmpBranchRef + ":" + GitUtils.createRemoteRef(myRoot.getRef());
      fetch(myTargetDirectory, refspec, true);
      myGitFactory.create(mirrorRepositoryDir).deleteBranch().setName(tmpBranchName).call();
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
    while (existingRefs.containsKey(branchName)) {
      branchName = tmpBranchName + i;
      i++;
    }
    return branchName;
  }
}
