package jetbrains.buildServer.buildTriggers.vcs.git.agent.errors;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
