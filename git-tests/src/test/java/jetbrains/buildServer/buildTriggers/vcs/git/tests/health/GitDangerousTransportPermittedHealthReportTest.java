package jetbrains.buildServer.buildTriggers.vcs.git.tests.health;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.health.GitDangerousTransportPermittedHealthReport;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitServerTestCase;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.serverSide.healthStatus.reports.StubHealthStatusItemConsumer;
import org.testng.annotations.DataProvider;
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
  private static Map<String, String> transportsOf(HealthStatusItem item) {
    return (Map<String, String>) item.getAdditionalData().get("transports");
  }

  private List<HealthStatusItem> reportWithOverride(String overrideValue, boolean globalItemsInScope) {
    if (overrideValue != null) setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, overrideValue);

    HealthStatusScope scope = new ScopeBuilder().addProject(myProject).setGlobalItems(globalItemsInScope).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();
    newReport().report(scope, consumer);
    return consumer.getConsumedItemsGlobal();
  }

  @DataProvider(name = "noItemCases")
  public Object[][] noItemCases() {
    return new Object[][]{
      {"default allowlist, no override", null, true},
      {"override only lists a non-dangerous transport", "hg", true},
      {"'ext' permitted but global items are not in scope", "ext", false}
    };
  }

  @Test(dataProvider = "noItemCases")
  public void reports_no_item(String caseLabel, String overrideValue, boolean globalItemsInScope) {
    then(reportWithOverride(overrideValue, globalItemsInScope)).as(caseLabel).isEmpty();
  }

  @DataProvider(name = "dangerousOverrideCases")
  public Object[][] dangerousOverrideCases() {
    return new Object[][]{
      {"ext", Arrays.asList("ext")},
      {"ext,fd", Arrays.asList("ext", "fd")}
    };
  }

  @Test(dataProvider = "dangerousOverrideCases")
  public void reports_one_global_item_naming_every_permitted_dangerous_transport(String overrideValue, List<String> expectedTransports) {
    List<HealthStatusItem> items = reportWithOverride(overrideValue, true);

    then(items).as("Expected exactly one global item for override '" + overrideValue + "'").hasSize(1);
    Map<String, String> transports = transportsOf(items.get(0));
    then(transports.keySet()).containsExactlyInAnyOrderElementsOf(expectedTransports);

    then(transports.get("ext")).as("ext executes a command").containsIgnoringCase("command");
    if (transports.containsKey("fd")) {
      then(transports.get("fd")).as("fd does not execute a command, unlike ext").doesNotContainIgnoringCase("command");
    }
  }
}
