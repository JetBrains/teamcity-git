

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface FetchCommand extends BaseCommand, AuthCommand<FetchCommand> {

  @NotNull
  FetchCommand setRefspec(@NotNull String refspec);

  @NotNull
  FetchCommand setQuite(boolean quite);

  @NotNull
  FetchCommand setShowProgress(boolean showProgress);

  @NotNull
  FetchCommand setDepth(int depth);

  @NotNull
  FetchCommand setFetchTags(boolean fetchTags);

  @NotNull
  FetchCommand setRemote(@NotNull String remoteUrl);

  @NotNull
  FetchCommand setRefSpecsRefresher(jetbrains.buildServer.buildTriggers.vcs.git.command.LsRemoteCommand lsRemote);

  void call() throws VcsException;

}