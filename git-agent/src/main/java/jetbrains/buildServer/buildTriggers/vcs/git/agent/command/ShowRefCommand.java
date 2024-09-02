

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import org.jetbrains.annotations.NotNull;

public interface ShowRefCommand extends BaseCommand {

  @NotNull
  ShowRefCommand setPattern(@NotNull String pattern);

  @NotNull
  ShowRefCommand showTags();

  @NotNull
  ShowRefResult call();

}