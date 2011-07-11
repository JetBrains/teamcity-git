/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.VersionCommand;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class GitDetectorImpl implements GitDetector {

  /** the default windows git executable paths */
  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS =
    {"C:\\Program Files\\Git\\bin", "C:\\Program Files (x86)\\Git\\bin", "C:\\cygwin\\bin"};
  /** Default UNIX paths */
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = {"/usr/local/bin", "/usr/bin", "/opt/local/bin", "/opt/bin"};
  /** windows executable name */
  @NonNls private static final String DEFAULT_WINDOWS_GIT = "git.exe";
  /** UNIX executable name */
  @NonNls private static final String DEFAULT_UNIX_GIT = "git";


  private final GitPathResolver myResolver;


  public GitDetectorImpl(@NotNull GitPathResolver resolver) {
    myResolver = resolver;
  }


  @NotNull
  public Pair<String, GitVersion> getGitPathAndVersion(@NotNull VcsRoot root, @NotNull BuildAgentConfiguration config, @NotNull AgentRunningBuild build) throws VcsException {
    String path = getPathFromRoot(root, config);
    if (path != null) {
      Loggers.VCS.info("Using vcs root's git: " + path);
    } else {
      path = build.getSharedBuildParameters().getEnvironmentVariables().get(Constants.GIT_PATH_ENV);
      if (path != null) {
        Loggers.VCS.info("Using git specified by " + Constants.GIT_PATH_ENV + ": " + path);
      } else {
        path = defaultGit();
        Loggers.VCS.info("Using default git: " + path);
      }
    }
    GitVersion version = getGitVersion(path);
    checkVersionIsSupported(path, version);

    return Pair.create(path, version);
  }


  private String getPathFromRoot(VcsRoot root, BuildAgentConfiguration config) throws VcsException {
    String path = root.getProperty(Constants.AGENT_GIT_PATH);
    if (path != null) {
      return myResolver.resolveGitPath(config, path);
    }
    return null;
  }


  private GitVersion getGitVersion(String path) throws VcsException {
    try {
      return new VersionCommand(path).version();
    } catch (VcsException e) {
      throw new VcsException("Unable to run git at path " + path, e);
    }
  }


  private void checkVersionIsSupported(String path, GitVersion version) throws VcsException {
    if (!version.isSupported())
      throw new VcsException("TeamCity supports Git version " + GitVersion.MIN + " or higher, found Git ("+ path +") has version " + version +
                             ". Please upgrade Git or use server-side checkout.");
  }

  /**
   * @return the default executable name depending on the platform
   */
  private String defaultGit() {
    String[] paths;
    String program;
    if (SystemInfo.isWindows) {
      program = DEFAULT_WINDOWS_GIT;
      paths = DEFAULT_WINDOWS_PATHS;
    } else {
      program = DEFAULT_UNIX_GIT;
      paths = DEFAULT_UNIX_PATHS;
    }
    for (String p : paths) {
      File f = new File(p, program);
      Loggers.VCS.info("Trying default git location: " + f.getPath());
      if (f.exists()) {
        return f.getAbsolutePath();
      }
    }
    Loggers.VCS.info(String.format("The git has not been found in default locations. Will use '%s' command without specified path.",
                                   SystemInfo.isWindows ? DEFAULT_WINDOWS_GIT : DEFAULT_UNIX_GIT));
    return SystemInfo.isWindows ? DEFAULT_WINDOWS_GIT : DEFAULT_UNIX_GIT;
  }

}
