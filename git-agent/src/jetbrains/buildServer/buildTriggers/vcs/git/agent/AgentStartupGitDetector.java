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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.config.AgentParametersSupplier;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class AgentStartupGitDetector implements AgentParametersSupplier {

  private final static Logger LOG = Logger.getLogger(AgentStartupGitDetector.class);

  static final String WIN_EXECUTABLE_NAME = "git.exe";
  static final String UNIX_EXECUTABLE_NAME = "git";
  private static final String[] WIN_PATHS = {"C:\\Program Files\\Git\\bin\\", "C:\\Program Files (x86)\\Git\\bin\\", "C:\\cygwin\\bin\\"};
  private static final String[] UNIX_PATHS = {"/usr/local/bin/", "/usr/bin/", "/opt/local/bin/", "/opt/bin/"};
  private static final String GIT_LFS_VERSION_PREFIX = "git-lfs/";
  @NotNull private final BuildAgentConfiguration myBuildAgentConfiguration;

  public AgentStartupGitDetector(@NotNull final ExtensionHolder extensionHolder, @NotNull final BuildAgentConfiguration buildAgentConfiguration) {
    myBuildAgentConfiguration = buildAgentConfiguration;
    extensionHolder.registerExtension(AgentParametersSupplier.class, getClass().getName(), this);
  }

  @Override
  public Map<String, String> getParameters() {
    final Map<String, String> parameters = new HashMap<>();

    String configuredGitPath = getConfiguredGitPath();
    if (configuredGitPath == null) {
      for (String path : getCandidatePaths()) {
        try {
          final GitVersion version = new AgentGitFacadeImpl(path).version().call();
          parameters.put(jetbrains.buildServer.agent.Constants.ENV_PREFIX + Constants.TEAMCITY_AGENT_GIT_VERSION, version.toString());
          if (version.isSupported()) {
            LOG.info("Detected git at " + path);
            setPathToGit(parameters, path);
            break;
          } else {
            LOG.debug("TeamCity supports Git version " + GitVersion.MIN + " or higher, git at " + path + " has version " + version + " and will not be used");
          }
        } catch (VcsException e) {
          LOG.debug("Cannot run git at " + path, e);
        }
      }
    } else {
      LOG.debug("Path to git configured: " + configuredGitPath + ", will not try to detect git");
      try {
        final GitVersion version = new AgentGitFacadeImpl(configuredGitPath).version().call();
        parameters.put(jetbrains.buildServer.agent.Constants.ENV_PREFIX + Constants.TEAMCITY_AGENT_GIT_VERSION, version.toString());
      } catch (VcsException e) {
        LOG.debug("Cannot run git at " + configuredGitPath, e);
      }
    }
    detectGitLfs(parameters);
    detectSSH(parameters);

    return parameters;
  }
  @Nullable
  private String getConfiguredGitPath() {
    Map<String, String> envVars = getBuildEnvironmentVariables();
    return envVars.get(Constants.TEAMCITY_AGENT_GIT_PATH);
  }

  private void setPathToGit(@NotNull Map<String, String> parameters, String path) {
    parameters.put(jetbrains.buildServer.agent.Constants.ENV_PREFIX + Constants.TEAMCITY_AGENT_GIT_PATH, path);
  }

  private Map<String, String> getBuildEnvironmentVariables() {
    return myBuildAgentConfiguration.getBuildParameters().getEnvironmentVariables();
  }

  private List<String> getCandidatePaths() {
    List<String> candidatePaths = new ArrayList<String>();
    String exec = getExecutableName();
    for (String path : getSearchPaths()) {
      File f = new File(path + exec);
      if (f.isFile())
        candidatePaths.add(f.getAbsolutePath());
    }
    candidatePaths.add(exec);
    return candidatePaths;
  }

  private String getExecutableName() {
    return SystemInfo.isWindows ? WIN_EXECUTABLE_NAME : UNIX_EXECUTABLE_NAME;
  }

  @NotNull
  private String[] getSearchPaths() {
    return SystemInfo.isWindows ? WIN_PATHS : UNIX_PATHS;
  }

  private void detectGitLfs(@NotNull Map<String, String> parameters) {
    try {
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setPassParentEnvs(true);
      cmd.setExePath("git-lfs");
      cmd.addParameter("env");
      cmd.setWorkingDirectory(FileUtil.createTempDirectory("gitLfs", "")); // workaround for https://youtrack.jetbrains.com/issue/TW-63074
      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, new byte[0]);
      if (result.getExitCode() != 0)
        return;
      for (String line : result.getOutLines()) {
        if (line.startsWith(GIT_LFS_VERSION_PREFIX)) {
          int idx = line.indexOf(" ");
          if (idx > 0) {
            String version = line.substring(GIT_LFS_VERSION_PREFIX.length(), idx);
            parameters.put("teamcity.gitLfs.version", version);
            break;
          }
        }
      }
    } catch (Throwable e) {
      LOG.debug("Cannot detect git-lfs", e);
    }
  }

  private void detectSSH(@NotNull Map<String, String> parameters) {
    try {
      final GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setExePath("ssh");
      cmd.addParameter("-V");

      final ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, new byte[0]);
      if (result.getExitCode() == 0) {
        final String line = result.getStderr();
        if (line.startsWith("OpenSSH_") && line.contains(",")) {
          final String version = line.substring(0, line.indexOf(",") - 1);
          parameters.put("teamcity.git.ssh.version", version);
        }
      }
    } catch (Throwable t) {
      LOG.debug("Cannot detect ssh", t);
    }
  }
}
