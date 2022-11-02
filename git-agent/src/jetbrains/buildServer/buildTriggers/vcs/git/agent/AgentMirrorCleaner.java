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
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class AgentMirrorCleaner implements DirectoryCleanersProvider {

  private final static Logger LOG = Logger.getInstance(AgentMirrorCleaner.class.getName());
  private final MirrorManager myMirrorManager;
  private final SubmoduleManager mySubmoduleManager;
  private final AgentTokenStorage myTokenStorage;

  public AgentMirrorCleaner(@NotNull MirrorManager mirrorManager, @NotNull SubmoduleManager submoduleManager, @NotNull AgentTokenStorage tokenStorage) {
    myMirrorManager = mirrorManager;
    mySubmoduleManager = submoduleManager;
    myTokenStorage = tokenStorage;
  }

  @NotNull
  public String getCleanerName() {
    return "Git mirrors cleaner";
  }

  public void registerDirectoryCleaners(@NotNull DirectoryCleanersProviderContext context,
                                        @NotNull DirectoryCleanersRegistry registry) {
    //feature toggle, may be removed after testing new code
    if (Boolean.parseBoolean(
      context.getRunningBuild().getSharedConfigParameters().getOrDefault("teamcity.git.mirrorsCleaner.useOldImplementation", "false"))) {
      oldImplementation(context, registry);
      return;
    }

    final Set<String> repositoriesUsedInBuild = getRunningBuildRepositories(context);

    final Set<File> mirrors = new HashSet<>(myMirrorManager.getMappings().values());

    for (File mirror : listFiles(myMirrorManager.getBaseMirrorsDir())) {
      if (!mirror.isDirectory()) {
        LOG.debug("Skipping non-mirror file: " + mirror.getAbsolutePath());
        continue;
      }
      final String name = mirror.getName();
      if (!myMirrorManager.isInvalidDirName(name)) {
        final String repository = myMirrorManager.getUrl(mirror.getName());
        if (repository != null && repositoriesUsedInBuild.contains(repository)) {
          return;
        }
      }

      registry.addCleaner(mirror, new Date(myMirrorManager.getLastUsedTime(mirror)), () -> {
        if (myMirrorManager.isInvalidDirName(name)) {
          myMirrorManager.removeMirrorDir(mirror);
          FileUtil.delete(mirror); // make sure mirror is deleted
          LOG.info("Found invalid mirror directory: " + mirror.getAbsolutePath() + ", removed it straight away");
          return;
        }

        final String repository = myMirrorManager.getUrl(mirror.getName());
        if (repository == null) {
          myMirrorManager.removeMirrorDir(mirror);
          FileUtil.delete(mirror); // make sure mirror is deleted
          LOG.info("Found unused mirror directory: " + mirror.getAbsolutePath() + ", removed it straight away");
          return;
        }

        if (!repositoriesUsedInBuild.contains(repository)) {
          if (isCleanupEnabled(mirror)) {
            LOG.debug("Register cleaner for mirror " + mirror.getAbsolutePath());
            registry.addCleaner(mirror, new Date(myMirrorManager.getLastUsedTime(mirror)));
          } else {
            LOG.debug("Clean-up is disabled in " + repository + " (" + mirror.getName() + ")");
          }
        }
      });
      mirrors.remove(mirror);
    }

    for (File mirror : mirrors) {
      myMirrorManager.removeMirrorDir(mirror);
      LOG.debug("Found non existing mirror directory: " + mirror.getAbsolutePath() + ", removed it from the list of mirrors");
    }
  }

  private void oldImplementation(@NotNull DirectoryCleanersProviderContext context, @NotNull DirectoryCleanersRegistry registry) {
    final Set<String> repositoriesUsedInBuild = getRunningBuildRepositories(context);
    final Set<File> mirrors = new HashSet<File>(myMirrorManager.getMappings().values());

    for (File mirror : listFiles(myMirrorManager.getBaseMirrorsDir())) {
      if (!mirror.isDirectory()) {
        LOG.debug("Skipping non-mirror file: " + mirror.getAbsolutePath());
        continue;
      }

      final String name = mirror.getName();
      if (myMirrorManager.isInvalidDirName(name)) {
        deleteEverywhere(mirrors, mirror);
        LOG.info("Found invalid mirror directory: " + mirror.getAbsolutePath() + ", removed it straight away");
        continue;
      }

      final String repository = myMirrorManager.getUrl(mirror.getName());
      if (repository == null) {
        deleteEverywhere(mirrors, mirror);
        LOG.info("Found unused mirror directory: " + mirror.getAbsolutePath() + ", removed it straight away");
        continue;
      }

      if (!repositoriesUsedInBuild.contains(repository)) {
        if (isCleanupEnabled(mirror)) {
          LOG.debug("Register cleaner for mirror " + mirror.getAbsolutePath());
          registry.addCleaner(mirror, new Date(myMirrorManager.getLastUsedTime(mirror)));
        } else {
          LOG.debug("Clean-up is disabled in " + repository + " (" + mirror.getName() + ")");
        }
      }
      mirrors.remove(mirror);
    }

    for (File mirror : mirrors) {
      myMirrorManager.removeMirrorDir(mirror);
      LOG.debug("Found non existing mirror directory: " + mirror.getAbsolutePath() + ", removed it from the list of mirrors");
    }
  }

  private void deleteEverywhere(final Set<File> mirrors, final File mirror) {
    if (mirrors.contains(mirror)) {
      myMirrorManager.removeMirrorDir(mirror);
      mirrors.remove(mirror);
    }
    FileUtil.delete(mirror); // make sure mirror is deleted
  }

  @NotNull
  private Collection<File> listFiles(@NotNull File dir) {
    final File[] files = dir.listFiles();
    return files == null || files.length == 0 ? Collections.<File>emptyList() : Arrays.asList(files);
  }

  private Set<String> getRunningBuildRepositories(@NotNull DirectoryCleanersProviderContext context) {
    Set<String> repositories = new HashSet<String>();
    for (VcsRootEntry entry : context.getRunningBuild().getVcsRootEntries()) {
      VcsRoot root = entry.getVcsRoot();
      if (!Constants.VCS_NAME.equals(root.getVcsName()))
        continue;
      try {
        GitVcsRoot gitRoot = new AgentGitVcsRoot(myMirrorManager, root, myTokenStorage);
        String repositoryUrl = gitRoot.getRepositoryFetchURL().toString();
        LOG.debug("Repository " + repositoryUrl + " is used in the build, its mirror won't be cleaned");
        addRepositoryWithSubmodules(repositories, gitRoot.getRepositoryFetchURL().toString());
      } catch (VcsException e) {
        LOG.warn("Error while creating git root " + root.getName() + ". If the root has a mirror on agent, the mirror might be cleaned", e);
      }
    }
    return repositories;
  }

  private void addRepositoryWithSubmodules(@NotNull Set<String> result, @NotNull String repository) {
    result.add(repository);
    for (String s : mySubmoduleManager.getSubmodules(repository)) {
      addRepositoryWithSubmodules(result, s);
    }
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
