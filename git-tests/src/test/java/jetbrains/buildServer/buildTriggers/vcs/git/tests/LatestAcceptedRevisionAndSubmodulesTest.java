

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.Collections;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCollectChangesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RevisionMatchedByCheckoutRulesCalculator.Result;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class LatestAcceptedRevisionAndSubmodulesTest extends BaseRemoteRepositoryTest {
  private PluginConfigBuilder myConfig;
  private File myRepo;

  public LatestAcceptedRevisionAndSubmodulesTest() {
    super("repo_for_checkout_rules_with_submodules");
  }


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    TestLogger logger = new TestLogger();
    logger.setLogLevel(Level.INFO);
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()))
      .setFetchAllRefsEnabled(true);
    myRepo = getRemoteRepositoryDir("repo_for_checkout_rules_with_submodules");
    File module1Repo = new File(myRepo.getParentFile(), "module1");
    copyRepository(dataFile("repo_for_checkout_rules_module1"), module1Repo);
    File module2Repo = new File(myRepo.getParentFile(), "module2");
    copyRepository(dataFile("repo_for_checkout_rules_module2"), module2Repo);

    setInternalProperty(GitCollectChangesPolicy.REVISION_BY_CHECKOUT_RULES_USE_DIFF_COMMAND, "true");
  }

  @Test(dataProvider = "nativeGit")
  public void test_rule_submodule_added(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));

    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myRepo)).withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT).build();

    Result rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:module2"),
                                                                                            "0cc4726cc3f1a3db11f8c8a47acb69d3515d402a",
                                                                                            "refs/heads/master",
                                                                                            Collections.singleton(
                                                                                              "d1ff728043f50d7deaa524b978ed94df8d7adc34"));
    then(rev.getRevision()).isEqualTo("4ecc4f42602de72476ebe0bc3bc58fac65960179");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:module1"),
                                                                                     "4ecc4f42602de72476ebe0bc3bc58fac65960179",
                                                                                     "refs/heads/master",
                                                                                     Collections.singleton(
                                                                                       "f4076e07a5d587408baaaf49e57573c346acfa65"));
    then(rev.getRevision()).isEqualTo("d7ea7f829cd1c230d740de548817543737c157d4");
  }

  @Test(dataProvider = "nativeGit")
  public void test_rule_submodule_changed(boolean withNativeGit) throws VcsException {
    setInternalProperty(GitRepoOperationsImpl.GIT_NATIVE_OPERATIONS_ENABLED, String.valueOf(withNativeGit));

    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myRepo)).withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT).build();

    Result rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:module2"),
                                                                                            "02a41094cf4993c43230c8738acdf29e91cbb0d6",
                                                                                            "refs/heads/master",
                                                                                            Collections.singleton(
                                                                                              "c2c8d7dc007f1a4a7cad2134f5e42beca0733800"));
    then(rev.getRevision()).isEqualTo("57f0352c60269c14f02f2863c7a35cf8573aead0");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:module2\n-:module2/test"),
                                                                                     "dc65ba0f3053d2248013e6dd0be3a61d57de44af",
                                                                                     "refs/heads/master",
                                                                                     Collections.singleton(
                                                                                       "02a41094cf4993c43230c8738acdf29e91cbb0d6"));
    then(rev.getRevision()).isEqualTo("af8389af87d5af3802bd71e9d4a7df963c68ace8");
  }

  @NotNull
  private GitVcsSupport git() {
    return gitSupport().withPluginConfig(myConfig).build();
  }
}