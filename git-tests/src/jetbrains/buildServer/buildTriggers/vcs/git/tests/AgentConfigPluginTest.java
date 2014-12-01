/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentMirrorConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
      allowing(myAgentConfig).getCacheDirectory("git"); will(returnValue(new File("")));
    }});

    myMirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(myAgentConfig), new HashCalculatorImpl());
  }


  public void test_default_idle_timeout() {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void test_idle_timeout() {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "60");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    assertEquals(60, config.getIdleTimeoutSeconds());
  }


  public void test_negative_idle_timeout() {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "-60");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void should_not_use_native_ssh_by_default() {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    assertFalse(config.isUseNativeSSH());
  }


  public void test_change_use_native_ssh() {
    myBuildSharedConfigParameters.put("teamcity.git.use.native.ssh", "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    assertTrue(config.isUseNativeSSH());
  }


  public void should_not_use_local_mirrors_by_default() throws Exception {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot();
    assertFalse(config.isUseLocalMirrors(root));
  }


  public void test_change_use_local_mirrors() throws Exception {
    myBuildSharedConfigParameters.put("teamcity.git.use.local.mirrors", "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot();
    assertTrue(config.isUseLocalMirrors(root));
  }


  public void when_mirrors_are_enabled_in_vcs_root_alternates_should_be_used() throws Exception {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot(Constants.USE_AGENT_MIRRORS, "true");
    assertTrue(config.isUseAlternates(root));
  }


  public void when_mirrors_are_enabled_in_vcs_root_mirrors_without_alternates_can_be_used() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY , PluginConfigImpl.VCS_ROOT_MIRRORS_STRATEGY_MIRRORS_ONLY);
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot(Constants.USE_AGENT_MIRRORS, "true");
    assertFalse(config.isUseAlternates(root));
    assertTrue(config.isUseLocalMirrors(root));
  }


  public void build_mirror_settings_take_precedence_over_root__alternates() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.USE_ALTERNATES, "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot(Constants.USE_AGENT_MIRRORS, "false");
    assertTrue(config.isUseAlternates(root));
  }


  public void build_mirror_settings_take_precedence_over_root__mirrors() throws Exception {
    myBuildSharedConfigParameters.put(PluginConfigImpl.USE_MIRRORS, "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN));
    GitVcsRoot root = gitVcsRoot(Constants.USE_AGENT_MIRRORS, "false");
    assertTrue(config.isUseLocalMirrors(root));
  }


  @NotNull
  private GitVcsRoot gitVcsRoot(String... properties) throws VcsException {
    Map<String, String> props = new HashMap<String, String>(map(properties));
    props.put(Constants.FETCH_URL, "git://some.org");
    return new GitVcsRoot(myMirrorManager, new VcsRootImpl(1, Constants.VCS_NAME, props));
  }


  public void test_path_to_git() {
    assertEquals("git", new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("git", GitVersion.MIN)).getPathToGit());
    assertEquals("/usr/bin/git", new PluginConfigImpl(myAgentConfig, myBuild, new GitExec("/usr/bin/git", GitVersion.MIN)).getPathToGit());
  }
}
