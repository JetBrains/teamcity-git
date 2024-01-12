

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * Detects git on agent
 * @author dmitry.neverov
 */
public interface GitDetector {

  @NotNull
  GitExec getGitPathAndVersion(@NotNull VcsRoot root, @NotNull BuildAgentConfiguration config, @NotNull AgentRunningBuild build) throws VcsException;

  @NotNull
  GitExec getGitPathAndVersion(@NotNull AgentRunningBuild build) throws VcsException;

}