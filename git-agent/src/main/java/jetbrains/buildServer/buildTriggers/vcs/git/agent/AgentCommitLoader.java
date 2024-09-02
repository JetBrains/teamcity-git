package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface AgentCommitLoader {

  boolean loadCommitInBranch(@NotNull String sha, @NotNull String branch, boolean enforceFetch) throws VcsException;

  boolean loadCommit(@NotNull String sha) throws VcsException;

  boolean loadCommitPreferShallow(@NotNull String sha, @NotNull String branch) throws VcsException;

  /**
   * @deprecated this method is only used backward compatibility and will be deleted
   */
  boolean loadShallowBranch(@NotNull String sha, @NotNull String branch) throws VcsException;
}
