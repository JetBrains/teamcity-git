package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface CommitGraphCommand extends BaseCommand {
  @NotNull
  CommitGraphCommand setVerifyCommand();

  @NotNull
  CommitGraphCommand setWriteCommand();

  @NotNull
  CommitGraphCommand setStrategy(@NotNull String strategy);

  @NotNull
  CommitGraphCommand setReachable();

  int call() throws VcsException;
}
