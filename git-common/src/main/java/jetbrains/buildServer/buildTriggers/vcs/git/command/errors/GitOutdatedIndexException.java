

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import jetbrains.buildServer.vcs.VcsException;

public class GitOutdatedIndexException extends VcsException {

  public GitOutdatedIndexException(final Throwable cause) {
    super(cause);
  }

}