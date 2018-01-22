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
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UpdaterWithAlternates extends UpdaterWithMirror {

  private final static Logger LOG = Logger.getLogger(UpdaterWithMirror.class);

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
                               @NotNull CheckoutMode mode) throws VcsException {
    super(fs, pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, mode);
  }


  @Override
  protected void setupExistingRepository() throws VcsException {
    setupAlternates();
    setupLfsStorage();
  }


  @Override
  protected void setupNewRepository() throws VcsException {
    setupAlternates();
    setupLfsStorage();
  }


  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    super.fetchFromOriginalRepository(fetchRequired);
  }


  private void setupLfsStorage() throws VcsException {
    //add lfs.storage = <mirror/lfs>
    GitFacade git = myGitFactory.create(myTargetDirectory);
    File mirrorLfs = new File(myRoot.getRepositoryDir(), "lfs");
    String lfsStorage = git.resolvePath(mirrorLfs);
    git.setConfig()
      .setPropertyName("lfs.storage")
      .setValue(lfsStorage)
      .call();
    File checkoutDirLfs = new File(new File(myTargetDirectory, ".git"), "lfs");
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


  private void setupAlternates() throws VcsException {
    File gitDir = new File(myTargetDirectory, ".git");
    if (!gitDir.exists())
      throw new IllegalStateException(gitDir.getAbsolutePath() + " doesn't exist");
    File objectsInfo = new File(new File(gitDir, "objects"), "info");
    objectsInfo.mkdirs();
    File alternates = new File(objectsInfo, "alternates");
    try {
      FileUtil.writeFileAndReportErrors(alternates, getAlternatePath());
      copyRefs();
    } catch (IOException e) {
      LOG.warn("Error while configuring alternates at " + alternates.getAbsoluteFile(), e);
      throw new VcsException(e);
    }
  }

  @NotNull
  private String getAlternatePath() throws VcsException {
    return myGitFactory.create(myTargetDirectory).resolvePath(new File(myRoot.getRepositoryDir(), "objects"));
  }

  private void copyRefs() {
    File targetDotGit = new File(myTargetDirectory, ".git");
    try {
      copyPackedRefs(targetDotGit);
    } catch (Exception e) {
      LOG.warn("Error while packing refs, will copy them one by one", e);
      copyRefsOneByOne(targetDotGit);
    }
  }

  private void copyRefsOneByOne(@NotNull File targetDotGit) {
    File srcDir = new File(myRoot.getRepositoryDir(), "refs");
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

  private void copyPackedRefs(@NotNull File targetDotGit) throws VcsException, IOException {
    //packed-refs were prepared during mirror update
    FileUtil.copy(new File(myRoot.getRepositoryDir(), "packed-refs"), new File(targetDotGit, "packed-refs"));
  }
}
