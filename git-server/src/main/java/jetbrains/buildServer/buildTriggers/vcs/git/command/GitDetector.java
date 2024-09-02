package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface GitDetector {
  @NotNull GitExec detectGit() throws VcsException;
}
