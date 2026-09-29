package jetbrains.buildServer.buildTriggers.vcs.git.tests.health;

import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.health.GitDangerousTransportPermittedHealthReport;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitServerTestCase;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.serverSide.healthStatus.reports.StubHealthStatusItemConsumer;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Tests for {@link GitDangerousTransportPermittedHealthReport}
 */
@Test
public class GitDangerousTransportPermittedHealthReportTest extends BaseGitServerTestCase {

  private GitDangerousTransportPermittedHealthReport newReport() {
    return new GitDangerousTransportPermittedHealthReport();
  }

  @SuppressWarnings("unchecked")
  private static Collection<String> transportsOf(HealthStatusItem item) {
    return (Collection<String>) item.getAdditionalData().get("transports");
  }

  @Test
  public void no_dangerous_transport_permitted_item_by_default() {
    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).setGlobalItems(true).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    then(consumer.getConsumedItemsGlobal()).as("No global item expected when no dangerous transport is permitted").isEmpty();
  }

  @Test
  public void reports_dangerous_transport_permitted_item_when_ext_is_allow_listed() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "ext");

    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).setGlobalItems(true).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    List<HealthStatusItem> globalItems = consumer.getConsumedItemsGlobal();
    then(globalItems).as("Expected exactly one global item").hasSize(1);
    then(transportsOf(globalItems.get(0))).contains("ext");
  }

  @Test
  public void reports_single_item_mentioning_both_ext_and_fd_when_both_are_allow_listed() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "ext,fd");

    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).setGlobalItems(true).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    List<HealthStatusItem> globalItems = consumer.getConsumedItemsGlobal();
    then(globalItems).as("Expected exactly one global item even with two dangerous transports permitted").hasSize(1);
    then(transportsOf(globalItems.get(0))).contains("ext", "fd");
  }

  @Test
  public void no_dangerous_transport_permitted_item_for_non_dangerous_override() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "hg");

    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).setGlobalItems(true).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    then(consumer.getConsumedItemsGlobal()).as("'hg' is not a known-dangerous transport").isEmpty();
  }

  @Test
  public void no_dangerous_transport_permitted_item_when_global_items_not_in_scope() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "ext");

    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).build(); // no setGlobalItems(true)
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    then(consumer.getConsumedItemsGlobal()).as("No global item expected when globalItems() is false").isEmpty();
  }
}
