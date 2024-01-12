

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface InitCommand extends BaseCommand {

  @NotNull
  InitCommand setBare(boolean bare);

  @NotNull
  InitCommandResult call() throws VcsException;

}