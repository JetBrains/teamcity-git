package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;

public interface FsckCommand extends BaseCommand {
  FsckCommand setConnectivityOnly();

  /**
   * Verifies the connectivity and validity of the objects in the database (https://git-scm.com/docs/git-fsck)
   * @return 0 if the repository is health,
   * non-zero return value is bitwise OR between problem types listed below (builtin/fsck.c):
   * ERROR_OBJECT 01
   * ERROR_REACHABLE 02
   * ERROR_PACK 04
   * ERROR_REFS 010
   * ERROR_COMMIT_GRAPH 020
   * ERROR_MULTI_PACK_INDEX 040
   * ERROR_PACK_REV_INDEX 0100
   * ERROR_BITMAP 0200
   */
  int call() throws VcsException;
}
