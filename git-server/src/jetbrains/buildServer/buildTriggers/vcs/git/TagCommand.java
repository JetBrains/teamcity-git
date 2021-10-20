package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface TagCommand extends GitCommand {
  @NotNull
  String tag(@NotNull OperationContext context,
             @NotNull String tag,
             @NotNull String commit) throws VcsException;
}
