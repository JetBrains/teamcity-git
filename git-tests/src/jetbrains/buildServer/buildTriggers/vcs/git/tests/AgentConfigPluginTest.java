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
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

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


  @BeforeMethod
  public void setUp() {
    myMockery = new Mockery();
    myAgentConfig = myMockery.mock(BuildAgentConfiguration.class);
    myBuild = myMockery.mock(AgentRunningBuild.class);
    myBuildSharedConfigParameters = new HashMap<String, String>();

    myMockery.checking(new Expectations() {{
      allowing(myBuild).getSharedConfigParameters();
      will(returnValue(myBuildSharedConfigParameters));
    }});
  }


  public void test_default_idle_timeout() {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void test_idle_timeout() {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "60");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertEquals(60, config.getIdleTimeoutSeconds());
  }


  public void test_negative_idle_timeout() {
    myBuildSharedConfigParameters.put("teamcity.git.idle.timeout.seconds", "-60");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertEquals(PluginConfig.DEFAULT_IDLE_TIMEOUT, config.getIdleTimeoutSeconds());
  }


  public void should_not_use_native_ssh_by_default() {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertFalse(config.isUseNativeSSH());
  }


  public void test_change_use_native_ssh() {
    myBuildSharedConfigParameters.put("teamcity.git.use.native.ssh", "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertTrue(config.isUseNativeSSH());
  }


  public void should_not_use_local_mirrors_by_default() {
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertFalse(config.isUseLocalMirrors());
  }


  public void test_change_use_local_mirrors() {
    myBuildSharedConfigParameters.put("teamcity.git.use.local.mirrors", "true");
    PluginConfigImpl config = new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN);
    assertTrue(config.isUseLocalMirrors());
  }


  public void test_path_to_git() {
    assertEquals("git", new PluginConfigImpl(myAgentConfig, myBuild, "git", GitVersion.MIN).getPathToGit());
    assertEquals("/usr/bin/git", new PluginConfigImpl(myAgentConfig, myBuild, "/usr/bin/git", GitVersion.MIN).getPathToGit());
  }
}
