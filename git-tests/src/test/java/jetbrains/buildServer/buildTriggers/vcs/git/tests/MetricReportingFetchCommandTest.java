package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchSettings.FetchMode;
import jetbrains.buildServer.metrics.NoOpCounter;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class MetricReportingFetchCommandTest extends BaseTestCase {

  public void fetch_is_timed_by_the_fetch_mode_the_fetch_command_used() throws Exception {
    // e.g. JGit fetches all refs when asked for the refspec refs/*:refs/* whatever the mode is
    List<FetchMode> timed = fetch(settings(), (db, fetchURI, s) -> s.getListenersMulticaster().fetchModeUsed(FetchMode.FETCH_ALL_REFS));

    then(timed).containsExactly(FetchMode.FETCH_ALL_REFS);
  }

  public void fetch_is_timed_by_the_requested_fetch_mode_when_the_fetch_command_reports_nothing() throws Exception {
    FetchSettings settings = settings();
    settings.setFetchMode(FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS);
    List<FetchMode> timed = fetch(settings, (db, fetchURI, s) -> {});

    then(timed).containsExactly(FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS);
  }

  public void failed_fetch_is_timed() throws Exception {
    List<FetchMode> timed = new ArrayList<>();
    MetricReportingFetchCommand command = new MetricReportingFetchCommand((db, fetchURI, s) -> {
      s.getListenersMulticaster().fetchModeUsed(FetchMode.FETCH_REF_SPECS);
      throw new VcsException("fetch failed");
    }, fetchMode -> {
      timed.add(fetchMode);
      return new NoOpCounter();
    });

    Throwable error = catchThrowable(() -> command.fetch(repository(), new URIish("http://some.org/repository"), settings()));

    then(error).isInstanceOf(VcsException.class);
    then(timed).containsExactly(FetchMode.FETCH_REF_SPECS);
  }

  public void listener_of_the_caller_receives_the_events_too() throws Exception {
    FetchSettings settings = settings();
    List<FetchMode> reportedToCaller = new ArrayList<>();
    settings.addListener(new FetchListener() {
      @Override
      public void fetchModeUsed(@NotNull FetchMode fetchMode) {
        reportedToCaller.add(fetchMode);
      }
    });

    List<FetchMode> timed = fetch(settings, (db, fetchURI, s) -> s.getListenersMulticaster().fetchModeUsed(FetchMode.FETCH_ALL_REFS));

    then(reportedToCaller).containsExactly(FetchMode.FETCH_ALL_REFS);
    then(timed).containsExactly(FetchMode.FETCH_ALL_REFS);
  }

  public void failing_listener_does_not_fail_the_fetch() throws Exception {
    FetchSettings settings = settings();
    settings.addListener(new FetchListener() {
      @Override
      public void fetchModeUsed(@NotNull FetchMode fetchMode) {
        throw new IllegalStateException("listener failed");
      }
    });

    List<FetchMode> timed = fetch(settings, (db, fetchURI, s) -> s.getListenersMulticaster().fetchModeUsed(FetchMode.FETCH_ALL_REFS));

    then(timed).containsExactly(FetchMode.FETCH_ALL_REFS);
  }

  public void tag_values() {
    then(MetricReportingFetchCommand.getFetchTypeTagValue(FetchMode.FETCH_REF_SPECS)).isEqualTo("refSpecs");
    then(MetricReportingFetchCommand.getFetchTypeTagValue(FetchMode.FETCH_ALL_REFS)).isEqualTo("allRefs");
    then(MetricReportingFetchCommand.getFetchTypeTagValue(FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS)).isEqualTo("allRefsExceptTags");
  }

  @NotNull
  private static List<FetchMode> fetch(@NotNull FetchSettings settings, @NotNull FetchCommand delegate) throws Exception {
    List<FetchMode> timed = new ArrayList<>();
    MetricReportingFetchCommand command = new MetricReportingFetchCommand(delegate, fetchMode -> {
      timed.add(fetchMode);
      return new NoOpCounter();
    });
    command.fetch(repository(), new URIish("http://some.org/repository"), settings);
    return timed;
  }

  @NotNull
  private static InMemoryRepository repository() {
    return new InMemoryRepository(new DfsRepositoryDescription("repository"));
  }

  @NotNull
  private static FetchSettings settings() {
    return new FetchSettings(new AuthSettingsImpl(vcsRoot().withFetchUrl("http://some.org/repository").build(), new URIishHelperImpl()));
  }
}
