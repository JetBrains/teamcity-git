package jetbrains.buildServer.buildTriggers.vcs.git.tests.health;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.health.GitLocalFileUrlHealthReport;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.impl.ScopeBuilder;
import jetbrains.buildServer.serverSide.healthStatus.reports.StubHealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitServerTestCase;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Tests for {@link GitLocalFileUrlHealthReport}
 */
@Test
public class GitLocalFileUrlHealthReportTest extends BaseGitServerTestCase {

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.registerVcsSupport(Constants.VCS_NAME);
    myFixture.registerVcsSupport("svn");
  }

  private static final String LOCAL_URL = "/var/repos/my.git";
  private static final String REMOTE_URL = "https://example.com/my.git";

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

  private GitLocalFileUrlHealthReport newReport() {
    return new GitLocalFileUrlHealthReport();
  }

  @Test
  public void does_nothing_when_property_disabled() {
    // given
    setInternalProperty(Constants.WARN_FILE_URL, "false");
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(LOCAL_URL, LOCAL_URL));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    then(consumer.getConsumedItems()).as("No items expected when WARN_FILE_URL is disabled").isEmpty();
  }

  @Test
  public void reports_for_plain_local_fetch_and_push() {
    // given
    ProjectEx p = myProject;
    SVcsRoot root = createGitRoot(p, props(LOCAL_URL, LOCAL_URL));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    List<HealthStatusItem> items = consumer.getConsumedItems();
    // Expect two items (for fetch and push)
    then(items).as("Expected 2 items (fetch + push)").hasSize(2);
    for (HealthStatusItem item : items) {
      // Data contains vcsRoot and url
      then(item.getAdditionalData().get("vcsRoot")).isSameAs(root);
      then(item.getAdditionalData().get("url")).isEqualTo(LOCAL_URL);
      then(item.getAdditionalData().get("buildType")).isNull();
      then(item.getIdentity()).as("Identity should be project-level when no BT context").contains("_BT_none");
    }
  }

  @Test
  public void does_not_care_about_references_without_build_type() {
    // given
    ProjectEx p = createProject("proj");
    // add parameter at project level
    p.addParameter(new SimpleParameter("ref", LOCAL_URL));

    SVcsRoot root = createGitRoot(p, props("%ref%", null)); // fetch has reference

    // Project scope without explicit build types
    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(root).build();
    then(scope.getBuildTypes()).as("Precondition: no build types in scope").isEmpty();

    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    then(consumer.getConsumedItems()).as("No items expected as VCS root with references is not attached to any build configuration").isEmpty();
  }

  @Test
  public void resolves_reference_per_build_type_scope() {
    // given
    ProjectEx p = myProject;
    BuildTypeImpl bt1 = myFixture.createBuildType(p, "bt1", "ant");
    BuildTypeImpl bt2 = myFixture.createBuildType(p, "bt2", "ant");

    // Parameter values differ per build type
    bt1.addParameter(new SimpleParameter("ref", LOCAL_URL));
    bt2.addParameter(new SimpleParameter("ref", REMOTE_URL)); // not local => should not report for bt2

    SVcsRoot root = createGitRoot(p, props("%ref%", "%ref%")); // both fetch and push use reference
    bt1.addVcsRoot(root);
    bt2.addVcsRoot(root);

    // Scope contains explicit build types
    HealthStatusScope scope = new ScopeBuilder().addProject(p).addBuildType(bt1).addBuildType(bt2).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    List<HealthStatusItem> items = consumer.getConsumedItems();
    // For bt1 we expect two items (fetch + push), bt2 should contribute none
    then(items).as("Expected 2 items only for bt1").hasSize(2);

    for (HealthStatusItem item : items) {
      then(item.getAdditionalData().get("url")).isEqualTo(LOCAL_URL);
      Object btObj = item.getAdditionalData().get("buildType");
      then(btObj).as("BT-scoped items must include buildType in data").isNotNull();
      SBuildType bt = (SBuildType)btObj;
      then(bt.getExternalId()).isEqualTo(bt1.getExternalId());
      then(item.getIdentity()).as("Identity must include BT external id").contains("_BT_" + bt1.getExternalId());
    }
  }

  @Test
  public void ignores_non_git_roots_and_remote_urls() {
    // given
    ProjectEx p = myProject;
    // Non-git root
    SVcsRoot nonGit = p.createVcsRoot("svn", "svn_root", java.util.Collections.emptyMap());
    // Git root with remote URLs
    SVcsRoot gitRemote = createGitRoot(p, props(REMOTE_URL, REMOTE_URL));

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addVcsRoot(nonGit).addVcsRoot(gitRemote).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    then(consumer.getConsumedItems()).as("No items expected for non-Git roots or remote URLs").isEmpty();
  }

  @Test
  public void ignores_unresolved_references() {
    // given
    ProjectEx p = myProject;
    BuildTypeImpl bt1 = myFixture.createBuildType(p, "bt1", "ant");

    SVcsRoot root = createGitRoot(p, props("%ref%", "%ref%")); // both fetch and push use reference
    bt1.addVcsRoot(root);

    HealthStatusScope scope = new ScopeBuilder().addProject(p).addBuildType(bt1).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    then(consumer.getConsumedItems()).as("No items expected because the references are unresolvable").isEmpty();
  }

  @Test
  public void resolves_reference_for_vcs_root_from_parent() {
    // given

    final ProjectEx parent = createProject("parent");

    final ProjectEx child = parent.createProject("child", "child");
    child.addParameter(new SimpleParameter("ref", LOCAL_URL));

    BuildTypeImpl bt1 = myFixture.createBuildType(child, "bt1", "ant");

    SVcsRoot root = createGitRoot(parent, props("%ref%", "%ref%")); // both fetch and push use reference
    bt1.addVcsRoot(root);

    HealthStatusScope scope = new ScopeBuilder().addProject(child).addBuildType(bt1).addVcsRoot(root).build();
    StubHealthStatusItemConsumer consumer = new StubHealthStatusItemConsumer();

    // when
    newReport().report(scope, consumer);

    // then
    List<HealthStatusItem> items = consumer.getConsumedItems();
    then(items).as("Expected 2 items only for bt1").hasSize(2);

    for (HealthStatusItem item : items) {
      then(item.getAdditionalData().get("url")).isEqualTo(LOCAL_URL);
      Object btObj = item.getAdditionalData().get("buildType");
      then(btObj).as("BT-scoped items must include buildType in data").isNotNull();
      SBuildType bt = (SBuildType)btObj;
      then(bt.getExternalId()).isEqualTo(bt1.getExternalId());
      then(item.getIdentity()).as("Identity must include BT external id").contains("_BT_" + bt1.getExternalId());
    }
  }
}
