

package jetbrains.buildServer.buildTriggers.vcs.git.tests.ssh;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.CurrentBuildTracker;
import jetbrains.buildServer.agent.NoRunningBuildException;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentSSHService;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.MockGitPluginDescriptor;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.MockVcsRootSshKeyManagerProvider;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class SSHAgentServiceTest extends BaseTestCase {
  public void check_classpath_is_correct() throws IOException {
    File tempDir = createTempDir();

    Mockery m = new Mockery();
    BuildAgent agent = m.mock(BuildAgent.class);
    BuildAgentConfiguration agentConf = m.mock(BuildAgentConfiguration.class);
    CurrentBuildTracker buildTracker = m.mock(CurrentBuildTracker.class);

    m.checking(new Expectations() {{
      allowing(agentConf).getConfigurationParameters(); will(returnValue(Collections.emptyMap()));
      allowing(agentConf).getTempDirectory(); will(returnValue(tempDir));
      allowing(buildTracker).getCurrentBuild(); will(throwException(new NoRunningBuildException()));
    }});

    GitAgentSSHService agentSSHService = new GitAgentSSHService(
      agentConf,
      new MockVcsRootSshKeyManagerProvider()
    );
    String scriptPath = agentSSHService.getScriptPath();

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(scriptPath);
    commandLine.addParameter("localhost");
    Map<String, String> env = new HashMap<>();
    env.put(GitSSHHandler.TEAMCITY_DEBUG_SSH, "true");
    commandLine.setEnvParams(env);
    ExecResult res = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);

    then(res.getStderr()).doesNotContain("NoClassDefFoundError").doesNotContain("ClassNotFoundError");
  }
}