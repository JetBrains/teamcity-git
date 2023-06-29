package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.List;
import jetbrains.buildServer.vcs.VcsException;

public interface AddCommandServer extends GitCommand {

  /**
   * add all files specified in parameter (including subfiles and subdirectories) into git index
   * @param repositoryPath repository path
   * @param paths list of paths to be added. Empty list is equal to "*" (i.e. will add all files)
   * @throws VcsException if and VCS exception occurs
   */
  void add(String repositoryPath, List<String> paths) throws VcsException;

}
