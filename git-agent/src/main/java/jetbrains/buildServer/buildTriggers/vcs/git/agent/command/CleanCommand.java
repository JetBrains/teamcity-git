

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanFilesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface CleanCommand extends BaseCommand {

  @NotNull
  CleanCommand setCleanPolicy(@NotNull AgentCleanFilesPolicy policy);

  @NotNull
  CleanCommand addExclude(@NotNull String path);

  void call() throws VcsException;

}