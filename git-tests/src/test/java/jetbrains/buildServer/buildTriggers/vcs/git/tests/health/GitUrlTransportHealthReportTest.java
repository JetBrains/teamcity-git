package jetbrains.buildServer.buildTriggers.vcs.git.tests.health;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.health.GitUrlTransportHealthReport;
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
 * Tests for {@link GitUrlTransportHealthReport}
 */
@Test
public class GitUrlTransportHealthReportTest extends BaseGitServerTestCase {

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

  private GitUrlTransportHealthReport newReport() {
    return new GitUrlTransportHealthReport();
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static Collection<String> transportsOf(@NotNull HealthStatusItem item) {
    return (Collection<String>) item.getAdditionalData().get("transports");
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
  public void item_identity_is_distinct_from_local_file_url_health_report() {
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(DISALLOWED_URL, null));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    newReport().report(scope, consumer);

    HealthStatusItem item = consumer.getConsumedItems().get(0);
    then(item.getIdentity()).doesNotContain("GitLocalFileUrlHealthReport");
    then(item.getIdentity()).contains(GitUrlTransportHealthReport.TYPE);
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
