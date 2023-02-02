package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommandResult;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2023.04
 */
public interface InitCommandServer extends GitCommand {

  /**
   * creates new repository in specified path, i.e. result will be like:
   * cd $path
   * git init ?(--bare)
   *
   * @param path path where repository should be initialized
   * @param bare if bare repository should be initialized
   */
  InitCommandResult init(@NotNull String path, boolean bare) throws VcsException;

  /**
   * the as {@link InitCommandServer#init(String, boolean)} but after initializing, the method commits current state. This method creates non-bare repository always
   * after this it creates .gitignore file if ignoredPaths list isn't empty, after this commit all matched file into created repository
   *
   * @param path           path where repository should be initialized
   * @param commitSettings settings of the commit (username and comment)
   */
  InitCommandResult initAndCommit(@NotNull String path, @NotNull CommitSettings commitSettings) throws VcsException;

}
