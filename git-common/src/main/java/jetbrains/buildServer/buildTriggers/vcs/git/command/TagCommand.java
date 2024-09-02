package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface TagCommand extends BaseCommand {

  @NotNull
  TagCommand setName(@NotNull String name);

  @NotNull
  TagCommand setCommit(@NotNull String sha);

  @NotNull
  TagCommand force(boolean force);

  @NotNull
  TagCommand delete(boolean delete);

  @NotNull
  TagCommand annotate(boolean annotate);

  @NotNull
  TagCommand setTagger(@NotNull String name, @NotNull String email);

  @NotNull
  TagCommand setMessage(@NotNull String msg);

  void call() throws VcsException;
}
