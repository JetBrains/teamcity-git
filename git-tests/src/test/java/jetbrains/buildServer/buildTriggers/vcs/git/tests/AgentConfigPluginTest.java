

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentMirrorConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.RemoteRepositoryUrlInvestigatorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentConfigPluginTest {

  private Mockery myMockery;
  private BuildAgentConfiguration myAgentConfig;
  private AgentRunningBuild myBuild;
  private Map<String, String> myBuildSharedConfigParameters;
  private MirrorManager myMirrorManager;

  @BeforeMethod
  public void setUp() {
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
    myMockery = new Mockery();
    myAgentConfig = myMockery.mock(BuildAgentConfiguration.class);
    myBuild = myMockery.mock(AgentRunningBuild.class);
    myBuildSharedConfigParameters = new HashMap<String, String>();

    myMockery.checking(new Expectations() {{
      allowing(myBuild).getSharedConfigParameters();
      will(returnValue(myBuildSharedConfigParameters));
      allowing(myBuild).getBuildLogger();
      allowing(myAgentConfig).getCacheDirectory("git"); will(returnValue(new File("")));
    }});

    myMirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(myAgentConfig), new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
  }

  public void test_default_custom_recoverable_messages() throws VcsException {
    PluginConfigImpl config = getPluginConfig();
    assertEquals(Collections.emptyList(), config.getCustomRecoverableMessages());
  }

  public void test_custom_recoverable_messages() throws VcsException {
    myBuildSharedConfigParameters.put("teamcity.git.agent.recoverableMessages", "msg1; msg2 ;    three ; msg4;");
    PluginConfigImpl config = getPluginConfig();
    List<String> expected = new ArrayList<String>();
    expected.add("msg1");
    expected.add(" msg2 ");
    expected.add("    three ");
    expected.add(" msg4");
    assertEquals(expected, config.getCustomRecoverableMessages());
  }

  public void test_default_idle_timeout() throws VcsException {
    PluginConfigImpl config = getPluginConfig();
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void test_idle_timeout() throws VcsException {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "60");
    PluginConfigImpl config = getPluginConfig();
    assertEquals(60, config.getIdleTimeoutSeconds());
  }


  public void test_negative_idle_timeout() throws VcsException {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "-60");
    PluginConfigImpl config = getPluginConfig();
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void should_not_use_native_ssh_by_default() throws VcsException {
    PluginConfigImpl config = getPluginConfig();
    assertFalse(config.isUseNativeSSH());
  }


  public void test_change_use_native_ssh() throws VcsException {
    myBuildSharedConfigParameters.put("teamcity.git.use.native.ssh", "true");
    PluginConfigImpl config = getPluginConfig();
    assertTrue(config.isUseNativeSSH());
  }


  public void should_not_use_local_mirrors_by_default() throws Exception {
    GitVcsRoot root = gitVcsRoot();
    PluginConfigImpl config = getPluginConfig(root);
    assertFalse(config.isUseLocalMirrors(root));
  }


  public void test_change_use_local_mirrors() throws Exception {
    myBuildSharedConfigParameters.put("teamcity.git.use.local.mirrors", "true");
    GitVcsRoot root = gitVcsRoot();
    PluginConfigImpl config = getPluginConfig(root);
    assertTrue(config.isUseLocalMirrors(root));
  }


  public void when_mirrors_are_enabled_in_vcs_root_alternates_should_be_used() throws Exception {
    GitVcsRoot root = gitVcsRoot(Constants.CHECKOUT_POLICY, "true");
    PluginConfigImpl config = getPluginConfig(root);
    assertTrue(config.isUseAlternates(root));
  }


  public void when_mirrors_are_enabled_in_vcs_root_mirrors_without_alternates_can_be_used() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY , PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY);
    GitVcsRoot root = gitVcsRoot(Constants.CHECKOUT_POLICY, "true");
    PluginConfigImpl config = getPluginConfig(root);
    assertFalse(config.isUseAlternates(root));
    assertTrue(config.isUseLocalMirrors(root));
  }


  public void build_mirror_settings_take_precedence_over_root__alternates() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.USE_ALTERNATES, "true");
    GitVcsRoot root = gitVcsRoot(Constants.CHECKOUT_POLICY, "false");
    PluginConfigImpl config = getPluginConfig(root);
    assertTrue(config.isUseAlternates(root));
  }


  public void build_mirror_settings_take_precedence_over_root__mirrors() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.USE_MIRRORS, "true");
    GitVcsRoot root = gitVcsRoot(Constants.CHECKOUT_POLICY, "false");
    PluginConfigImpl config = getPluginConfig(root);
    assertTrue(config.isUseLocalMirrors(root));
  }

  @NotNull
  private PluginConfigImpl getPluginConfig(String... properties) throws VcsException {
    return getPluginConfig(gitVcsRoot(properties));
  }

  @NotNull
  private PluginConfigImpl getPluginConfig(GitVcsRoot root) {
    return new PluginConfigImpl(myAgentConfig, myBuild, root.getOriginalRoot(), new GitExec("git", GitVersion.MIN));
  }

  @NotNull
  private GitVcsRoot gitVcsRoot(String... properties) throws VcsException {
    return new GitVcsRoot(myMirrorManager, getVcsRoot(properties), new URIishHelperImpl());
  }

  @NotNull
  private VcsRootImpl getVcsRoot(String... properties) {
    Map<String, String> props = new HashMap<String, String>(map(properties));
    props.put(Constants.FETCH_URL, "git://some.org");
    return new VcsRootImpl(1, Constants.VCS_NAME, props);
  }


  public void test_path_to_git() throws VcsException {
    assertEquals("git", getPluginConfig().getPathToGit());
    assertEquals("/usr/bin/git", new PluginConfigImpl(myAgentConfig, myBuild, getVcsRoot(), new GitExec("/usr/bin/git", GitVersion.MIN)).getPathToGit());
  }
}