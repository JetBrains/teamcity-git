package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import jetbrains.buildServer.metrics.Counter;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class MetricReportingFetchCommand implements FetchCommand {
  private final FetchCommand myDelegate;
  private final Function<FetchSettings.FetchMode, Counter> myFetchDurationTimers;

  /**
   * @param fetchDurationTimers the fetch duration timer of the repository by the kind of fetch the delegate uses
   */
  public MetricReportingFetchCommand(@NotNull final FetchCommand delegate, @NotNull Function<FetchSettings.FetchMode, Counter> fetchDurationTimers) {
    myDelegate = delegate;
    myFetchDurationTimers = fetchDurationTimers;
  }

  @Override
  public void fetch(@NotNull final Repository db,
                    @NotNull final URIish fetchURI,
                    @NotNull final FetchSettings settings) throws IOException, VcsException {
    AtomicReference<FetchSettings.FetchMode> usedFetchMode = new AtomicReference<>();
    FetchListener listener = new FetchListener() {
      @Override
      public void fetchModeUsed(@NotNull FetchSettings.FetchMode fetchMode) {
        usedFetchMode.set(fetchMode);
      }
    };
    settings.addListener(listener);
    // not Guava's Stopwatch: the separate patch process runs this command too, and its classpath has no Guava, see PluginConfigImpl.getPatchClasspath()
    long startNanos = System.nanoTime();
    try {
      myDelegate.fetch(db, fetchURI, settings);
    } finally {
      settings.removeListener(listener);
      // a fetch failed before choosing what to fetch is reported as requested
      FetchSettings.FetchMode fetchMode = usedFetchMode.get() != null ? usedFetchMode.get() : settings.getFetchMode();
      myFetchDurationTimers.apply(fetchMode).increment(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }
  }

  /**
   * @return the value of the "fetchType" tag of the fetch duration metric
   */
  @NotNull
  public static String getFetchTypeTagValue(@NotNull FetchSettings.FetchMode fetchMode) {
    switch (fetchMode) {
      case FETCH_ALL_REFS:
        return "allRefs";
      case FETCH_ALL_REFS_EXCEPT_TAGS:
        return "allRefsExceptTags";
      case FETCH_REF_SPECS:
      default:
        return "refSpecs";
    }
  }
}
