package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2023.05
 */
public interface LocalCommitCommandServer extends GitCommand {

  /**
   * add all files specified in parameter (including subfiles and subdirectories) and commit these files into repository
   *
   * @param repositoryPath repository path
   * @param commitSettings settings for commit
   * @throws VcsException if and VCS exception occurs
   */
  void commit(String repositoryPath, @NotNull CommitSettings commitSettings) throws VcsException;

}
