

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DiffCommand {

  @NotNull
  DiffCommand setCommit1(@NotNull String commit1);

  @NotNull
  DiffCommand setCommit2(@NotNull String commit2);

  @NotNull
  DiffCommand setFormat(@NotNull String format);

  @NotNull
  List<String> call() throws VcsException;

}