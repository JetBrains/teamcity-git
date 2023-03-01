package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2023.04
 */
public interface LocalCommitCommandServer extends GitCommand {

  void commit(String repositoryPath, @NotNull CommitSettings commitSettings) throws VcsException;

}
