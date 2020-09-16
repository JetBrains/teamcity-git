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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpdaterWithAlternates extends UpdaterWithMirror {

  private final static Logger LOG = Logger.getLogger(UpdaterWithMirror.class);

  @NotNull private final File myGitDir;

  public UpdaterWithAlternates(@NotNull FS fs,
                               @NotNull AgentPluginConfig pluginConfig,
                               @NotNull MirrorManager mirrorManager,
                               @NotNull SmartDirectoryCleaner directoryCleaner,
                               @NotNull GitFactory gitFactory,
                               @NotNull AgentRunningBuild build,
                               @NotNull VcsRoot root,
                               @NotNull String version,
                               @NotNull File targetDir,
                               @NotNull CheckoutRules rules,
                               @NotNull CheckoutMode mode,
                               @NotNull SubmoduleManager submoduleManager) throws VcsException {
    super(fs, pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, mode, submoduleManager);
    myGitDir = new File(myTargetDirectory, ".git");
  }


  @Override
  protected void setupExistingRepository() throws VcsException {
    removeUrlSections();
    setupRepository(myGitDir, myRoot.getRepositoryDir());
  }

  @Override
  protected void setupNewRepository() throws VcsException {
    setupRepository(myGitDir, myRoot.getRepositoryDir());
  }


  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    super.fetchFromOriginalRepository(fetchRequired);
  }

  private void setupRepository(@NotNull File gitDir, @NotNull File mirrorDir) throws VcsException {
    setupAlternates(gitDir, mirrorDir);
    setupLfsStorage(gitDir, mirrorDir);
  }

  private void setupLfsStorage(@NotNull File gitDir, @NotNull File mirrorDir) throws VcsException {
    //add lfs.storage = <mirror/lfs>
    GitFacade git = myGitFactory.create(gitDir);
    File mirrorLfs = new File(mirrorDir, "lfs");
    String lfsStorage = git.resolvePath(mirrorLfs);
    git.setConfig()
      .setPropertyName("lfs.storage")
      .setValue(lfsStorage)
      .call();
    File checkoutDirLfs = new File(gitDir, "lfs");
    if (!mirrorLfs.exists() && checkoutDirLfs.isDirectory()) {
      //If mirror doesn't have lfs storage and checkout dir has one, copy it to
      //the mirror in order to not fetch lfs files from scratch. This situation occurs
      //after upgrade to the TC version supporting lfs.storage and after enabling
      //mirrors in Git VCS root settings.
      if (!checkoutDirLfs.renameTo(mirrorLfs)) {
        LOG.info("Failed to move lfs storage to the mirror repository " + mirrorLfs.getAbsoluteFile() + ", large files will be fetched from scratch");
      }
    }
  }


  private void setupAlternates(@NotNull File gitDir, @NotNull File mirrorDir) throws VcsException {
    if (!gitDir.exists())
      throw new IllegalStateException(gitDir.getAbsolutePath() + " doesn't exist");
    File objectsInfo = new File(new File(gitDir, "objects"), "info");
    objectsInfo.mkdirs();
    File alternates = new File(objectsInfo, "alternates");
    try {
      FileUtil.writeFileAndReportErrors(alternates, getAlternatePath(gitDir, mirrorDir));
      copyRefs(gitDir, mirrorDir);
    } catch (IOException e) {
      LOG.warn("Error while configuring alternates at " + alternates.getAbsoluteFile(), e);
      throw new VcsException(e);
    }
  }

  @NotNull
  private String getAlternatePath(@NotNull File gitDir, @NotNull File mirrorDir) throws VcsException {
    return myGitFactory.create(gitDir).resolvePath(new File(mirrorDir, "objects"));
  }

  private void copyRefs(@NotNull File gitDir, @NotNull File mirrorDir) {
    try {
      copyPackedRefs(gitDir, mirrorDir);
    } catch (Exception e) {
      LOG.warn("Error while packing refs, will copy them one by one", e);
      copyRefsOneByOne(gitDir, mirrorDir);
    }
  }

  private void copyRefsOneByOne(@NotNull File targetDotGit, @NotNull File mirrorDir) {
    File srcDir = new File(mirrorDir, "refs");
    File dstDir = new File(targetDotGit, "refs");
    List<String> files = new ArrayList<String>();
    FileUtil.listFilesRecursively(srcDir, "", false, Integer.MAX_VALUE, null, files);
    for (String f : files) {
      File dstRef = new File(dstDir, f);
      if (!dstRef.exists()) {
        try {
          FileUtil.copy(new File(srcDir, f), dstRef);
        } catch (IOException e) {
          LOG.warn("Error while copying refs, refs will be created during fetch", e);
        }
      }
    }
  }

  private void copyPackedRefs(@NotNull File targetDotGit, @NotNull File mirrorDir) throws VcsException, IOException {
    //packed-refs were prepared during mirror update
    FileUtil.copy(new File(mirrorDir, "packed-refs"), new File(targetDotGit, "packed-refs"));
  }

  @Override
  protected void updateSubmodules(@NotNull final File repositoryDir) throws VcsException, IOException, ConfigInvalidException {
    if (!myPluginConfig.isUseLocalMirrorsForSubmodules(myRoot)) {
      super.updateSubmodules(repositoryDir);
      return;
    }

    final Map<String, AggregatedSubmodule> submodules = getSubmodules(repositoryDir);
    persistSubmodules(repositoryDir, submodules.keySet());

    for (AggregatedSubmodule aggregatedSubmodule : submodules.values()) {
      final File mirrorRepositoryDir = updateSubmoduleMirror(aggregatedSubmodule);

      for (Submodule s : aggregatedSubmodule.getSubmodules()) {
        final File submoduleDir = new File(repositoryDir, s.getPath());
        final File submoduleGitDir = GitUtils.getGitDir(submoduleDir);

        final GitFacade gitFacade = myGitFactory.create(submoduleDir);
        if (!submoduleGitDir.exists())  {
          submoduleGitDir.mkdirs();
          gitFacade.init().call();
        }

        setUseRemoteSubmoduleOrigin(submoduleGitDir, aggregatedSubmodule.getUrl());
        setupRepository(submoduleGitDir, mirrorRepositoryDir);
        removeRefLocks(submoduleGitDir);

        checkout(gitFacade).setForce(true).setBranch(s.getRevision()).setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds()).call();
      }
    }
  }
}
