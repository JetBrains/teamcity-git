package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistryImpl;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.MultiNodesEvents;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import jetbrains.buildServer.serverSide.impl.beans.VcsRootInstanceContext;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.MockSVcsRoot;
import jetbrains.buildServer.vcs.RepositoryStateListener;
import jetbrains.buildServer.vcs.VcsUtil;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.impl.VcsRootInstanceImpl;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.Test;

@Test
public class GitHubPasswordAuthRootRegistryImplTest extends BaseTestCase {
  @Test
  public void test() {
    final Map<String, Long> events = new HashMap<>();
    final MockTimeService timeService = new MockTimeService();

    final Map<String, String> vcsProperties = CollectionsUtil.asMap(
      VcsUtil.VCS_NAME_PROP, Constants.VCS_NAME,
      Constants.AUTH_METHOD, AuthenticationMethod.PASSWORD.name(),
      Constants.FETCH_URL, "https://github.com/name/repo.git",
      Constants.PASSWORD, "%some.param%");

    final Mockery mockery = new Mockery();
    final ProjectManager projectManager = mockery.mock(ProjectManager.class);
    final ServerResponsibility serverResponsibility = mockery.mock(ServerResponsibility.class);
    mockery.checking(new Expectations() {
      {
        allowing(projectManager).findVcsRootById(1);
        final MockSVcsRoot vcsRoot = new MockSVcsRoot(1, Constants.VCS_NAME);
        vcsRoot.setProperties(vcsProperties);
        will(returnValue(vcsRoot));

        allowing(serverResponsibility).canCheckForChanges();
        will(returnValue(true));
      }
    });

    final GitHubPasswordAuthRootRegistryImpl registry =
      new GitHubPasswordAuthRootRegistryImpl(new EventDispatcher<BuildServerListener>(BuildServerListener.class) {},
                                             new EventDispatcher<RepositoryStateListener>(RepositoryStateListener.class) {
                                             },
                                             projectManager,
                                             serverResponsibility,
                                             createMultiNodesEvents(events),
                                             timeService);

    vcsProperties.put(Constants.PASSWORD, "12345");
    registry.update(new VcsRootImpl(1, vcsProperties));

    assertTrue(registry.containsVcsRoot(1));
    BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime);
    BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageAdd", 1L);

    events.clear();
    timeService.inc();
    registry.update(new VcsRootImpl(1, vcsProperties));
    assertTrue(registry.containsVcsRoot(1));
    BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime);
    assertTrue(events.isEmpty());

    timeService.inc();
    final VcsRootInstanceContext context = mockery.mock(VcsRootInstanceContext.class);
    mockery.checking(new Expectations() {
      {
        allowing(context).getTimeService();
        will(returnValue(timeService));
        allowing(context).getServerMetrics();
        will(returnValue(null));
      }
    });
    registry.update(new VcsRootInstanceImpl(100, Constants.VCS_NAME, 1, "My Name", vcsProperties, context));
    assertTrue(registry.containsVcsRoot(1));
    BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime);
    assertTrue(events.isEmpty());

    timeService.inc();
    vcsProperties.put(Constants.PASSWORD, "1234567890abcdef1234567890abcdef12345678");
    registry.update(new VcsRootImpl(1, vcsProperties));
    assertTrue(registry.containsVcsRoot(1));
    BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime - 1);
    assertTrue(events.isEmpty());

    setInternalProperty("teamcity.git.gitHubPasswordAuthHealthReport.updateIntervalSec", "300");
    timeService.inc(350, TimeUnit.SECONDS);
    registry.update(new VcsRootImpl(1, vcsProperties));
    assertFalse(registry.containsVcsRoot(1));
    assertTrue(registry.getRegistry().isEmpty());
    BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageRemove", 1L);

    events.clear();
    timeService.inc();
    vcsProperties.put(Constants.PASSWORD, "12345");
    registry.update(new VcsRootImpl(1, vcsProperties));

    assertTrue(registry.containsVcsRoot(1));
    BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime);
    BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageAdd", 1L);

    events.clear();
    timeService.inc();
    vcsProperties.put(Constants.PASSWORD, "gho_oPan0zUSMxvI7NoWDBjjBP965641HX2NHNbu"); // see TW-71026
    timeService.inc(350, TimeUnit.SECONDS);
    registry.update(new VcsRootImpl(1, vcsProperties));
    assertFalse(registry.containsVcsRoot(1));
    assertTrue(registry.getRegistry().isEmpty());
    BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageRemove", 1L);

    {
      // add root to registry again
      events.clear();
      timeService.inc();
      vcsProperties.put(Constants.PASSWORD, "12345");
      registry.update(new VcsRootImpl(1, vcsProperties));

      assertTrue(registry.containsVcsRoot(1));
      BaseTestCase.assertMap(registry.getRegistry(), 1L, timeService.myTime);
      BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageAdd", 1L);
    }

    {
      // TW-72780
      events.clear();
      timeService.inc();
      vcsProperties.put(Constants.AUTH_METHOD, AuthenticationMethod.ANONYMOUS.name());
      timeService.inc(350, TimeUnit.SECONDS);
      registry.update(new VcsRootImpl(1, vcsProperties));
      assertFalse(registry.containsVcsRoot(1));
      assertTrue(registry.getRegistry().isEmpty());
      BaseTestCase.assertMap(events, "gitHubPasswordAuthUsageRemove", 1L);
    }

    {
      // TW-73295
      events.clear();
      timeService.inc();
      timeService.inc(350, TimeUnit.SECONDS);
      registry.update(new VcsRootImpl(1, vcsProperties));
      assertFalse(registry.containsVcsRoot(1));
      assertTrue(registry.getRegistry().isEmpty());
      assertTrue(events.isEmpty());
    }
  }

  @NotNull
  public MultiNodesEvents createMultiNodesEvents(@NotNull final Map<String, Long> published) {
    return new MultiNodesEvents() {
      @Override
      public void publish(@NotNull String eventName) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void publish(@NotNull String eventName, @NotNull Long longArg) {
        published.put(eventName, longArg);
      }

      @Override
      public void publish(@NotNull String eventName, @NotNull String strArg) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void publish(@NotNull EventData eventData) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void subscribe(@NotNull String eventName, @NotNull Consumer<Event> eventConsumer) {
      }

      @Override
      public void subscribeOnEvents(@NotNull String eventName, @NotNull Consumer<List<Event>> eventConsumer) throws IllegalArgumentException {
      }

      @Override
      public void unsubscribe(@NotNull String eventName) {

      }

      @Override
      public void ensureEventsProcessed() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean hasUnpublishedEvent(@NotNull EventData eventData) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean hasUnpublishedEvents(@NotNull Filter<EventData> eventsFilter) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void ensureEventsProcessed(@NotNull Filter<EventData> eventsFilter) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean eventsPublishingEnabled() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
