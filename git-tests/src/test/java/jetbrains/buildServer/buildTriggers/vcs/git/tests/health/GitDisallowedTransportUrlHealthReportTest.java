package jetbrains.buildServer.buildTriggers.vcs.git.tests.health;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.health.GitDisallowedTransportUrlHealthReport;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitServerTestCase;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.serverSide.healthStatus.reports.StubHealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Tests for {@link GitDisallowedTransportUrlHealthReport}
 */
@Test
public class GitDisallowedTransportUrlHealthReportTest extends BaseGitServerTestCase {

  private static final String DISALLOWED_URL = "ext::sh /tmp/x.sh";
  private static final String ALLOWED_URL = "https://example.com/my.git";

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.registerVcsSupport(Constants.VCS_NAME);
  }

  @NotNull
  private SVcsRoot createGitRoot(@NotNull ProjectEx project, @NotNull Map<String, String> props) {
    return project.createVcsRoot(Constants.VCS_NAME, "git_root", props);
  }

  private static Map<String, String> props(String fetch, String push) {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    if (fetch != null) b.put(Constants.FETCH_URL, fetch);
    if (push != null) b.put(Constants.PUSH_URL, push);
    return b.build();
  }

  private GitDisallowedTransportUrlHealthReport newReport() {
    return new GitDisallowedTransportUrlHealthReport();
  }

  @Test
  public void reports_disallowed_transport_fetch_url() {
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(DISALLOWED_URL, ALLOWED_URL));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    List<HealthStatusItem> items = consumer.getConsumedItems();
    then(items).as("Expected exactly one item for the disallowed fetch URL").hasSize(1);

    HealthStatusItem item = items.get(0);
    then(item.getAdditionalData().get("vcsRoot")).isSameAs(root);
    then(item.getAdditionalData().get("url")).isEqualTo(DISALLOWED_URL);
  }


  @Test
  public void reports_both_fetch_and_push_disallowed_transport_urls() {
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(DISALLOWED_URL, "fd::something"));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    then(consumer.getConsumedItems()).as("Expected 2 items (fetch + push)").hasSize(2);
  }

  @Test
  public void allowing_the_transport_via_override_property_clears_the_item() {
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(DISALLOWED_URL, null));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);
    then(consumer.getConsumedItems()).hasSize(1);

    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "ext");
    StubHealthStatusItemConsumer consumerAfter = new StubHealthStatusItemConsumer();
    newReport().report(scope, consumerAfter);

    then(consumerAfter.getConsumedItems()).as("Item should disappear once 'ext' is allow-listed").isEmpty();
  }

  @Test
  public void reports_no_items_for_allowed_transport_url() {
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(ALLOWED_URL, ALLOWED_URL));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    then(consumer.getConsumedItems()).as("No items expected for an allowed-transport URL").isEmpty();
  }

  @Test
  public void resolves_reference_per_build_type_scope() {
    ProjectEx p = myProject;
    BuildTypeImpl bt1 = myFixture.createBuildType(p, "bt1", "ant");
    BuildTypeImpl bt2 = myFixture.createBuildType(p, "bt2", "ant");

    bt1.addParameter(new SimpleParameter("ref", DISALLOWED_URL));
    bt2.addParameter(new SimpleParameter("ref", ALLOWED_URL)); // resolves to allowed => should not report for bt2

    SVcsRoot root = createGitRoot(p, props("%ref%", null));
    bt1.addVcsRoot(root);
    bt2.addVcsRoot(root);

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addBuildType(bt1).addBuildType(bt2).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    List<HealthStatusItem> items = consumer.getConsumedItems();
    then(items).as("Expected 1 item only for bt1").hasSize(1);

    HealthStatusItem item = items.get(0);
    then(item.getAdditionalData().get("url")).isEqualTo(DISALLOWED_URL);
    Object btObj = item.getAdditionalData().get("buildType");
    then(btObj).as("BT-scoped items must include buildType in data").isNotNull();
    SBuildType bt = (SBuildType) btObj;
    then(bt.getExternalId()).isEqualTo(bt1.getExternalId());
  }
}
