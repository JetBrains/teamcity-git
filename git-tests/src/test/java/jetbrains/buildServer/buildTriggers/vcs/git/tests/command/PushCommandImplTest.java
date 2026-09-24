package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.PushCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.PushCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class PushCommandImplTest extends BaseTestCase {

  @Override
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
  }

  @TestFor(issues = "TW-104064")
  public void should_add_separator_before_remote_url() throws Exception {
    final List<String> capturedParams = new ArrayList<>();
    AgentGitCommandLine cmd = new AgentGitCommandLine(null, getFakeGen(), new StubContext("git", new GitVersion(2, 30, 0))) {
      @Override
      public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
        capturedParams.addAll(getParametersList().getList());
        throw new VcsException("stop before running a real process");
      }
    };

    PushCommand push = new PushCommandImpl(cmd)
      .setRemote("https://example.com/repo.git")
      .setRefspec("refs/heads/master:refs/heads/master")
      .setAuthSettings(getEmptyAuthSettings());

    try {
      push.call();
      fail("Expected VcsException");
    } catch (VcsException e) {
      // expected: thrown by the stub run() before a real process would have been started
    }

    int remoteIdx = capturedParams.indexOf("https://example.com/repo.git");
    assertTrue("Remote URL parameter not found in: " + capturedParams, remoteIdx > 0);
    assertEquals("--", capturedParams.get(remoteIdx - 1));
  }

  @NotNull
  private AuthSettings getEmptyAuthSettings() {
    return new AuthSettingsImpl(new HashMap<String, String>(), new URIishHelperImpl());
  }

  @NotNull
  private ScriptGen getFakeGen() throws IOException {
    return new ScriptGen(createTempDir()) {
      @NotNull
      public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
        return createTempFile();
      }

      @NotNull
      @Override
      public File generateAskPass(@NotNull final String password) throws IOException {
        return createTempFile();
      }

      @NotNull
      @Override
      public File generateCredentialHelper() throws IOException {
        return createTempFile();
      }
    };
  }
}
