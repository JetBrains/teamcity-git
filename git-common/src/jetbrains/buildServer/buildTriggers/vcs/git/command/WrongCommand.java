package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
* This command (git wrong) does not exist. It was created to determine which locale git uses by error message text
*/

public interface WrongCommand extends BaseCommand {
  @NotNull
  String call() throws VcsException;
}
