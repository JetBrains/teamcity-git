package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import org.jetbrains.annotations.NotNull;

public interface GitRepoOperations {
  @NotNull
  FetchCommand fetchCommand();
}
