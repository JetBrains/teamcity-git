

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import java.io.File;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GitIndexCorruptedException extends VcsException {

  private final File myGitIndex;

  public GitIndexCorruptedException(@NotNull File gitIndex, Throwable cause) {
    super("Git index corrupted " + gitIndex.getAbsolutePath(), cause);
    myGitIndex = gitIndex;
  }

  @NotNull
  public File getGitIndex() {
    return myGitIndex;
  }
}