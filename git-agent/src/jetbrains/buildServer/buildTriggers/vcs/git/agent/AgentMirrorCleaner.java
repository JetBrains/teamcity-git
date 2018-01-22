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
import jetbrains.buildServer.agent.DirectoryCleanersProvider;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AgentMirrorCleaner implements DirectoryCleanersProvider {

  private final static Logger ourLog = Logger.getInstance(AgentMirrorCleaner.class.getName());
  private final MirrorManager myMirrorManager;

  public AgentMirrorCleaner(@NotNull MirrorManager mirrorManager) {
    myMirrorManager = mirrorManager;
  }

  @NotNull
  public String getCleanerName() {
    return "Git mirrors cleaner";
  }

  public void registerDirectoryCleaners(@NotNull DirectoryCleanersProviderContext context,
                                        @NotNull DirectoryCleanersRegistry registry) {
    Set<String> repositoriesUsedInBuild = getRunningBuildRepositories(context);
    for (Map.Entry<String, File> entry : myMirrorManager.getMappings().entrySet()) {
      String repository = entry.getKey();
      File mirror = entry.getValue();
      if (!repositoriesUsedInBuild.contains(repository)) {
        if (isCleanupEnabled(mirror)) {
          ourLog.debug("Register cleaner for mirror " + mirror.getAbsolutePath());
          registry.addCleaner(mirror, new Date(myMirrorManager.getLastUsedTime(mirror)));
        } else {
          ourLog.debug("Clean-up is disabled in " + repository + " (" + mirror.getName() + ")");
        }
      }
    }
  }

  private Set<String> getRunningBuildRepositories(@NotNull DirectoryCleanersProviderContext context) {
    Set<String> repositories = new HashSet<String>();
    for (VcsRootEntry entry : context.getRunningBuild().getVcsRootEntries()) {
      VcsRoot root = entry.getVcsRoot();
      if (!Constants.VCS_NAME.equals(root.getVcsName()))
        continue;
      try {
        GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, root);
        String repositoryUrl = gitRoot.getRepositoryFetchURL().toString();
        ourLog.debug("Repository " + repositoryUrl + " is used in the build, its mirror won't be cleaned");
        repositories.add(gitRoot.getRepositoryFetchURL().toString());
      } catch (VcsException e) {
        ourLog.warn("Error while creating git root " + root.getName() + ". If the root has a mirror on agent, the mirror might be cleaned", e);
      }
    }
    return repositories;
  }

  private boolean isCleanupEnabled(@NotNull File gitDir) {
    Repository repository = null;
    try {
      repository = new RepositoryBuilder().setGitDir(gitDir).setBare().build();
      return repository.getConfig().getBoolean("teamcity", "freeDiskSpaceCleanupEnabled", true);
    } catch (Exception e) {
      return true;
    } finally {
      if (repository != null) {
        repository.close();
      }
    }
  }
}
