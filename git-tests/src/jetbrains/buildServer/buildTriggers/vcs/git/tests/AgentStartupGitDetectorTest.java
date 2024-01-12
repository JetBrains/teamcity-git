

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildParametersMap;
import jetbrains.buildServer.agent.config.AgentParametersSupplier;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentStartupGitDetector;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author dmitry.neverov
 */
@Test
public class AgentStartupGitDetectorTest {
  private Mockery myContext;
  private AgentStartupGitDetector myDetector;
  private BuildAgentConfiguration config;


  @BeforeMethod
  public void setUp() {
    myContext = new Mockery();
    config = myContext.mock(BuildAgentConfiguration.class);
    final ExtensionHolder extensionHolder = myContext.mock(ExtensionHolder.class);
    myContext.checking(new Expectations(){{
      exactly(1).of(extensionHolder).registerExtension(with(AgentParametersSupplier.class), with(AgentStartupGitDetector.class.getName()), with(aNonNull(AgentStartupGitDetector.class)));
    }});
    myDetector = new AgentStartupGitDetector(extensionHolder, config);
  }

  public void should_do_nothing_if_path_to_git_set_by_user() {
    myContext.checking(new Expectations() {{
      BuildParametersMap paramsMap = myContext.mock(BuildParametersMap.class);
      Map<String, String> params = new HashMap<String, String>() {{put(Constants.TEAMCITY_AGENT_GIT_PATH, "/some/path/to/git");}};
      atLeast(1).of(config).getBuildParameters(); will(returnValue(paramsMap));
      atLeast(1).of(paramsMap).getEnvironmentVariables(); will(returnValue(params));
    }});
    myDetector.getParameters();

    myContext.assertIsSatisfied();
  }

  private void should_set_path_to_detected_git_if_not_set_by_user() {
    myContext.checking(new Expectations() {{
      BuildParametersMap paramsMap = myContext.mock(BuildParametersMap.class);
      Map<String, String> params = new HashMap<String, String>();
      atLeast(1).of(config).getBuildParameters(); will(returnValue(paramsMap));
      atLeast(1).of(paramsMap).getEnvironmentVariables(); will(returnValue(params));
      atLeast(1).of(config).addEnvironmentVariable(with(Constants.TEAMCITY_AGENT_GIT_PATH), with(any(String.class)));
    }});
    myDetector.getParameters();

    myContext.assertIsSatisfied();
  }
}