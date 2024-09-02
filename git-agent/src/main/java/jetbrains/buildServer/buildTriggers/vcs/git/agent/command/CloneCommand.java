

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface CloneCommand extends BaseCommand {

  @NotNull
  CloneCommand setMirror(boolean mirror);

  @NotNull
  CloneCommand setRepo(@NotNull String repoUrl);

  @NotNull
  CloneCommand setFolder(@NotNull String folder);

  void call() throws VcsException;

}