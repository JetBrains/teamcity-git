

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.List;
import java.util.concurrent.Callable;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
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
  FetchCommand setRefSpecsRefresher(Callable<List<Ref>> lsBranchRefresher);

  @NotNull
  FetchCommand setCommitGraphRefresher(Callable<Integer> commitGraphRefresher) throws VcsException;

  @NotNull
  FetchCommand setNoShowForcedUpdates(boolean noShowForcedUpdates);

  void call() throws VcsException;

}