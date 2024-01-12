

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import jetbrains.buildServer.metrics.Counter;
import jetbrains.buildServer.metrics.Stoppable;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class MetricReportingFetchCommand implements FetchCommand {
  private final FetchCommand myDelegate;
  private final Counter myFetchDurationTimer;

  public MetricReportingFetchCommand(@NotNull final FetchCommand delegate, @NotNull Counter fetchDurationTimer) {
    myDelegate = delegate;
    myFetchDurationTimer = fetchDurationTimer;
  }

  @Override
  public void fetch(@NotNull final Repository db,
                    @NotNull final URIish fetchURI,
                    @NotNull final FetchSettings settings) throws IOException, VcsException {
    try (Stoppable ignored = myFetchDurationTimer.startMsecsTimer()) {
      myDelegate.fetch(db, fetchURI, settings);
    }
  }
}