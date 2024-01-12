

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;

public interface VcsOperation<T> {
  T run() throws VcsException;
}