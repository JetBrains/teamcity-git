

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;

public interface VcsAction {
  void run() throws VcsException;
}