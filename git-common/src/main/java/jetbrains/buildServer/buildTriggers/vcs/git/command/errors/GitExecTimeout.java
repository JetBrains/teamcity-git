

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GitExecTimeout extends VcsException {
  public GitExecTimeout() {
    super("Timeout exception");
  }

  public GitExecTimeout(@NotNull Throwable cause) {
    super("Timeout exception: " + cause.toString(), cause);
  }
}