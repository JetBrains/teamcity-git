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

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class UpdaterWithAlternates extends UpdaterWithMirror {

  private final static Logger LOG = Logger.getLogger(UpdaterWithMirror.class);

  public UpdaterWithAlternates(@NotNull AgentPluginConfig pluginConfig,
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
  protected void setupExistingRepository() throws VcsException {
    setupAlternates();
  }


  @Override
  protected void setupNewRepository() throws VcsException {
    setupAlternates();
  }


  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    super.fetchFromOriginalRepository(fetchRequired);
  }


  private void setupAlternates() throws VcsException {
    File gitDir = new File(myTargetDirectory, ".git");
    if (!gitDir.exists())
      throw new IllegalStateException(gitDir.getAbsolutePath() + " doesn't exist");
    File objectsInfo = new File(new File(gitDir, "objects"), "info");
    objectsInfo.mkdirs();
    File alternates = new File(objectsInfo, "alternates");
    try {
      FileUtil.writeFileAndReportErrors(alternates, new File(myRoot.getRepositoryDir(), "objects").getCanonicalPath());
    } catch (IOException e) {
      LOG.warn("Error while configuring alternates at " + alternates.getAbsoluteFile(), e);
      throw new VcsException(e);
    }
  }
}
