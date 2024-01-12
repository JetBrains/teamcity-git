

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface UpdateRefCommand extends BaseCommand {

  @NotNull
  UpdateRefCommand setRef(@NotNull String ref);

  @NotNull
  UpdateRefCommand delete();

  @NotNull
  UpdateRefCommand setRevision(@NotNull String revision);

  @NotNull
  UpdateRefCommand setOldValue(@NotNull String v);

  void call() throws VcsException;

}