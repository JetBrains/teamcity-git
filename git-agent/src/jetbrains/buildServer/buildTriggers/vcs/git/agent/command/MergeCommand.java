package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface MergeCommand extends BaseCommand {
  @NotNull
  MergeCommand setBranches(final String... mergeBranches);

  @NotNull
  MergeCommand setAbort(boolean abort);

  @NotNull
  MergeCommand setQuiet(boolean quite);

  void call() throws VcsException;
}
