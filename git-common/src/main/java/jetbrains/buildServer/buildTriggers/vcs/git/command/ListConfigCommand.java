package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface ListConfigCommand extends BaseCommand{
  @NotNull
  String call() throws VcsException;
}
