package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommandResult;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2023.05
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

}
