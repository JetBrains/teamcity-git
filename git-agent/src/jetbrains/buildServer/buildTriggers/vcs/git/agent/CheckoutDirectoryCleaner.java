/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.DirectoryCleanersProvider;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.agent.impl.directories.DirectoryMap;
import jetbrains.buildServer.buildTriggers.vcs.git.CommandLineUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckoutDirectoryCleaner implements DirectoryCleanersProvider {

  public static final int DEFAULT_COMMAND_TIMEOUT_SEC = 1200;
  public static final int COMMAND_OUTPUT_THRESHOLD = 8 * 1024 * 1024;
  private final static Logger LOG = Logger.getInstance(CheckoutDirectoryCleaner.class.getName());
  @NotNull private final DirectoryMap myDirectoryMap;
  @NotNull private final GitDetector myGitDetector;

  public CheckoutDirectoryCleaner(@NotNull final DirectoryMap directoryMap,
                                  @NotNull final GitDetector gitDetector) {
    myDirectoryMap = directoryMap;
    myGitDetector = gitDetector;
  }

  private static boolean isGitRepo(@NotNull File root) {
    return new File(root, ".git").isDirectory();
  }

  private static boolean isGitCleanEnabled(@NotNull File repo) {
    try (Repository repository = new RepositoryBuilder().setGitDir(new File(repo, ".git")).build()) {
      return repository.getConfig().getBoolean("teamcity", "freeDiskSpaceCleanupEnabled", true);
    } catch (Exception e) {
      return true;
    }
  }

  private static int getCleanCommandTimeout(@NotNull File repo) {
    try (Repository repository = new RepositoryBuilder().setGitDir(new File(repo, ".git")).build()) {
      return repository.getConfig().getInt("teamcity", "freeDiskSpaceGitCleanupTimeout", DEFAULT_COMMAND_TIMEOUT_SEC);
    } catch (Exception e) {
      return DEFAULT_COMMAND_TIMEOUT_SEC;
    }
  }

  @NotNull
  @Override
  public String getCleanerName() {
    return "'git clean' checkout directory cleaner";
  }

  @Override
  public void registerDirectoryCleaners(@NotNull final DirectoryCleanersProviderContext context,
                                        @NotNull final DirectoryCleanersRegistry registry) {
    final AgentRunningBuild build = context.getRunningBuild();
    final Map<File, Date> folders = myDirectoryMap.getRegisteredRemovableItems(build);
    if (folders.isEmpty()) return;

    final String gitPath = getGitPath(build);
    if (gitPath == null) return;

    for (Map.Entry<File, Date> e : folders.entrySet()) {
      final File root = e.getKey();
      if (!isGitRepo(root) || !isGitCleanEnabled(root)) continue;

      registry.addCleaner(root, e.getValue(), () -> clean(root, gitPath));
    }
  }

  @Nullable
  private String getGitPath(final @NotNull AgentRunningBuild build) {
    try {
      return myGitDetector.getGitPathAndVersion(build).getPath();
    } catch (VcsException e) {
      LOG.warn("Failed to detect git, no cleaners will be registered", e);
    }
    return null;
  }

  private void clean(@NotNull File repo, @NotNull String pathToGit) {
    runGitCommand(repo, pathToGit, "git clean -f -d -x", getCleanCommandTimeout(repo), "clean", "-f", "-d", "-x");
  }

  // protected for tests
  protected void runGitCommand(@NotNull File repo, @NotNull String pathToGit, @NotNull String cmdName, int timeout, @NotNull String... params) {
    final String cmd = " '" + cmdName + "' in repo " + repo.getAbsolutePath();
    try {
      final GeneralCommandLine cl = new GeneralCommandLine();
      cl.setWorkingDirectory(repo);
      cl.setExePath(pathToGit);
      cl.addParameters(params);

      final long start = System.currentTimeMillis();
      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, null, new SimpleCommandLineProcessRunner.ProcessRunCallback() {
        public void onProcessStarted(@NotNull Process ps) {
          LOG.debug("Start" + cmd);
        }
        public void onProcessFinished(@NotNull Process ps) {
          final long finish = System.currentTimeMillis();
          LOG.debug("Finish" + cmd + ", duration: " + (finish - start) + "ms");
        }
        public Integer getOutputIdleSecondsTimeout() {
          return timeout;
        }
        public Integer getMaxAcceptedOutputSize() {
          return COMMAND_OUTPUT_THRESHOLD;
        }
      });

      final VcsException commandError = CommandLineUtil.getCommandLineError(cmd, result);
      if (commandError != null) {
        LOG.warnAndDebugDetails("Error while running" + cmd, commandError);
      }
      if (result.getStderr().length() > 0) {
        LOG.debug("Output produced by" + cmd);
        LOG.debug(result.getStderr());
      }
    } catch (Exception e) {
      LOG.debug("Error while running" + cmd, e);
    }
  }
}
