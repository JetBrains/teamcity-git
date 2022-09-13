/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildParametersMap;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentStartupGitDetector;
import jetbrains.buildServer.util.EventDispatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentStartupGitDetectorTest {
  private Mockery myContext;
  private AgentStartupGitDetector myDetector;

  @BeforeMethod
  public void setUp() {
    myContext = new Mockery();
    myDetector = new AgentStartupGitDetector(EventDispatcher.create(AgentLifeCycleListener.class));
  }

  public void should_do_nothing_if_path_to_git_set_by_user() {
    final BuildAgent agent = myContext.mock(BuildAgent.class);
    myContext.checking(new Expectations() {{
      BuildAgentConfiguration config = myContext.mock(BuildAgentConfiguration.class);
      BuildParametersMap paramsMap = myContext.mock(BuildParametersMap.class);
      Map<String, String> params = new HashMap<String, String>() {{put(Constants.TEAMCITY_AGENT_GIT_PATH, "/some/path/to/git");}};
      atLeast(1).of(agent).getConfiguration(); will(returnValue(config));
      atLeast(1).of(config).getBuildParameters(); will(returnValue(paramsMap));
      atLeast(1).of(paramsMap).getEnvironmentVariables(); will(returnValue(params));
    }});
    myDetector.afterAgentConfigurationLoaded(agent);

    myContext.assertIsSatisfied();
  }

  private void should_set_path_to_detected_git_if_not_set_by_user() {
    final BuildAgent agent = myContext.mock(BuildAgent.class);
    myContext.checking(new Expectations() {{
      BuildAgentConfiguration config = myContext.mock(BuildAgentConfiguration.class);
      BuildParametersMap paramsMap = myContext.mock(BuildParametersMap.class);
      Map<String, String> params = new HashMap<String, String>();
      atLeast(1).of(agent).getConfiguration(); will(returnValue(config));
      atLeast(1).of(config).getBuildParameters(); will(returnValue(paramsMap));
      atLeast(1).of(paramsMap).getEnvironmentVariables(); will(returnValue(params));
      atLeast(1).of(config).addEnvironmentVariable(with(Constants.TEAMCITY_AGENT_GIT_PATH), with(any(String.class)));
    }});
    myDetector.afterAgentConfigurationLoaded(agent);

    myContext.assertIsSatisfied();
  }
}
