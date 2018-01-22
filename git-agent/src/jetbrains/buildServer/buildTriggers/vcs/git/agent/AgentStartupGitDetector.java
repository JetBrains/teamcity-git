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
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class AgentStartupGitDetector extends AgentLifeCycleAdapter {

  private final static Logger LOG = Logger.getLogger(AgentStartupGitDetector.class);

  static final String WIN_EXECUTABLE_NAME = "git.exe";
  static final String UNIX_EXECUTABLE_NAME = "git";
  private static final String[] WIN_PATHS = {"C:\\Program Files\\Git\\bin\\", "C:\\Program Files (x86)\\Git\\bin\\", "C:\\cygwin\\bin\\"};
  private static final String[] UNIX_PATHS = {"/usr/local/bin/", "/usr/bin/", "/opt/local/bin/", "/opt/bin/"};
  private static final String GIT_LFS_VERSION_PREFIX = "git-lfs/";

  public AgentStartupGitDetector(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher) {
    dispatcher.addListener(this);
  }
  @Override
  public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
    if (pathToGitConfigured(agent)) {
      LOG.debug("Path to git configured, will not try to detect git");
      return;
    }
    for (String path : getCandidatePaths()) {
      try {
        GitVersion version = new NativeGitFacade(path, GitProgressLogger.NO_OP).version().call();
        if (version.isSupported()) {
          LOG.info("Detect git at " + path);
          setPathToGit(agent, path);
          break;
        } else {
          LOG.debug("TeamCity supports Git version " + GitVersion.MIN + " or higher, git at " + path + " has version " + version + " and will not be used");
        }
      } catch (VcsException e) {
        LOG.debug("Cannot run git at " + path, e);
      }
    }
    detectGitLfs(agent);
  }

  private boolean pathToGitConfigured(@NotNull BuildAgent agent) {
    Map<String, String> envVars = getEnvironmentVariables(agent);
    return envVars.get(Constants.TEAMCITY_AGENT_GIT_PATH) != null;
  }

  private void setPathToGit(@NotNull BuildAgent agent, String path) {
    agent.getConfiguration().addEnvironmentVariable(Constants.TEAMCITY_AGENT_GIT_PATH, path);
  }

  private Map<String, String> getEnvironmentVariables(@NotNull BuildAgent agent) {
    return agent.getConfiguration().getBuildParameters().getEnvironmentVariables();
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

  private void detectGitLfs(@NotNull BuildAgent agent) {
    try {
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setPassParentEnvs(true);
      cmd.setExePath("git-lfs");
      cmd.addParameter("env");
      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, new byte[0]);
      if (result.getExitCode() != 0)
        return;
      for (String line : result.getOutLines()) {
        if (line.startsWith(GIT_LFS_VERSION_PREFIX)) {
          int idx = line.indexOf(" ");
          if (idx > 0) {
            String version = line.substring(GIT_LFS_VERSION_PREFIX.length(), idx);
            agent.getConfiguration().addConfigurationParameter("teamcity.gitLfs.version", version);
            break;
          }
        }
      }
    } catch (Exception e) {
      LOG.debug("Cannot detect git-lfs", e);
    }
  }
}
