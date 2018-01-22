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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author dmitry.neverov
 */
public class GitDetectorImpl implements GitDetector {

  private final GitPathResolver myResolver;

  public GitDetectorImpl(@NotNull GitPathResolver resolver) {
    myResolver = resolver;
  }


  @NotNull
  public GitExec getGitPathAndVersion(@NotNull VcsRoot root, @NotNull BuildAgentConfiguration config, @NotNull AgentRunningBuild build) throws VcsException {
    String path = getPathFromRoot(root, config);
    if (path != null) {
      Loggers.VCS.info("Using vcs root's git: " + path);
    } else {
      path = build.getSharedBuildParameters().getEnvironmentVariables().get(Constants.TEAMCITY_AGENT_GIT_PATH);
      if (path != null) {
        Loggers.VCS.info("Using git specified by " + Constants.TEAMCITY_AGENT_GIT_PATH + ": " + path);
      } else {
        path = defaultGit();
        Loggers.VCS.info("Using default git: " + path);
      }
    }
    GitVersion version = getGitVersion(path);
    checkVersionIsSupported(path, version);
    return new GitExec(path, version, getCygwinBinPath(path));
  }

  @Nullable
  private String getCygwinBinPath(@NotNull String gitPath) {
    if (!SystemInfo.isWindows)
      return null;
    try {
      File git = new File(gitPath);
      if (git.isAbsolute()) {
        File cygpath = new File(git.getParentFile(), "cygpath.exe");
        if (cygpath.canExecute())
          return git.getParentFile().getCanonicalPath();
        return null;
      } else {
        String path = System.getenv("PATH");
        if (path == null) {
          Loggers.VCS.info("Cannot detect cygwin path, PATH environment variable is empty");
          return null;
        }
        String[] paths = path.split(Pattern.quote(File.pathSeparator));
        for (String p : paths) {
          if (new File(p, "git.exe").canExecute() && new File(p, "cygpath.exe").canExecute())
            return p;
        }
        return null;
      }
    } catch (Exception e) {
      Loggers.VCS.info("Error while detecting cygwin path", e);
      return null;
    }
  }


  private String getPathFromRoot(VcsRoot root, BuildAgentConfiguration config) throws VcsException {
    String path = root.getProperty(Constants.AGENT_GIT_PATH);
    if (path != null) {
      return myResolver.resolveGitPath(config, path);
    }
    return null;
  }


  @NotNull
  private GitVersion getGitVersion(String path) throws VcsException {
    try {
      return new NativeGitFacade(path, GitProgressLogger.NO_OP).version().call();
    } catch (VcsException e) {
      throw new VcsException("Unable to run git at path " + path, e);
    }
  }


  private void checkVersionIsSupported(String path, GitVersion version) throws VcsException {
    if (!version.isSupported())
      throw new VcsException("TeamCity supports Git version " + GitVersion.MIN + " or higher, found Git ("+ path +") has version " + version +
                             ". Please upgrade Git or use server-side checkout.");
  }

  private String defaultGit() {
    return SystemInfo.isWindows ? AgentStartupGitDetector.WIN_EXECUTABLE_NAME: AgentStartupGitDetector.UNIX_EXECUTABLE_NAME;
  }
}
