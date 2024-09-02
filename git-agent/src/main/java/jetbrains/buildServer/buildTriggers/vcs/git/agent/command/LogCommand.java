

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LogCommand extends BaseCommand {

  @NotNull
  LogCommand setStartPoint(@NotNull String startPoint);

  @NotNull
  LogCommand setCommitsNumber(int commitsNumber);

  @NotNull
  LogCommand setPrettyFormat(@NotNull String format);


  @Nullable
  String call();
}