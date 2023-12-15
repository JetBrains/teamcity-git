package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TagCommand extends GitCommand {
  @NotNull
  String tag(@NotNull OperationContext context,
             @NotNull String tag,
             @Nullable String message,
             @NotNull String commit) throws VcsException;
}
