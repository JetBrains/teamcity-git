package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseAuthCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Checks how {@link BaseAuthCommandImpl} reports a failed git command: a single line headline taken from the git
 * output plus the unmodified original error after "Full error: ".
 */
@Test
public class BaseAuthCommandImplTest extends BaseTestCase {

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
      });
    }};
  }

  public void should_report_single_fatal_line_and_the_full_original_error() throws Exception {
    String fatalLine = "Error fetching mirror repository git@github.com:JetBrains/teamcity-project-dsl.git: Short read of block. [request #ssh-7d42-257745]";
    String errorText =
      "git -c credential.helper= ls-remote origin command failed." +
      "\nexit code: 128" +
      "\nstderr: OpenSSH_8.7p1, OpenSSL 3.5.5 27 Jan 2026" +
      "\ndebug1: Sending command: git-upload-pack '/tc/TeamCity-BuildServer-Settings.git'" +
      "\ndebug2: channel 0: rcvd ext data 140" +
      "\nfatal: " + fatalLine +
      "\ndebug2: channel 0: written 140 to efd 6" +
      "\nfatal: Could not read from remote repository." +
      "\ndebug1: Exit status 42";

    final VcsException original = new VcsException(errorText);

    final VcsException reported = runFailingCommand(original);

    then(reported.getMessage()).isEqualTo(fatalLine + " \nFull error: " + errorText);
    then(reported.getCause()).isSameAs(original);
  }

  public void should_report_single_remote_line_and_the_full_original_error() throws Exception {
    String error = "Invalid username or password.";
    final VcsException original = new VcsException(
      "git ls-remote origin command failed." +
      "\nexit code: 128" +
      "\nstderr: remote: " + error +
      "\nfatal: Authentication failed for 'https://some.org/repo.git/'");

    final VcsException reported = runFailingCommand(original);

    then(reported.getMessage()).isEqualTo(error + " \nFull error: " + original.getMessage());
    then(reported.getCause()).isSameAs(original);
  }

  public void should_rethrow_error_without_fatal_or_remote_line_as_is() throws Exception {
    final VcsException original = new VcsException(
      "git ls-remote origin command failed." +
      "\nexit code: 128" +
      "\nstderr: some output without a headline");

    then(runFailingCommand(original)).isSameAs(original);
  }

  @NotNull
  private VcsException runFailingCommand(@NotNull VcsException failure) throws Exception {
    final GitCommandLine failingCmd = new GitCommandLine(new StubContext(), fakeScriptGen()) {
      @Override
      public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
        throw failure;
      }
    };

    final TestAuthCommand cmd = new TestAuthCommand(failingCmd).setAuthSettings(emptyAuthSettings());

    final Throwable thrown = catchThrowable(cmd::run);
    then(thrown).isInstanceOf(VcsException.class);
    return (VcsException)thrown;
  }

  @NotNull
  private AuthSettings emptyAuthSettings() {
    return new AuthSettingsImpl(new HashMap<>(), new URIishHelperImpl());
  }

  @NotNull
  private ScriptGen fakeScriptGen() throws IOException {
    return new ScriptGen(createTempDir()) {
      @NotNull
      @Override
      public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
        return createTempFile();
      }

      @NotNull
      @Override
      public File generateAskPass(@Nullable String password) throws IOException {
        return createTempFile();
      }

      @NotNull
      @Override
      public File generateCredentialHelper() throws IOException {
        return createTempFile();
      }
    };
  }

  private static class TestAuthCommand extends BaseAuthCommandImpl<TestAuthCommand> {
    TestAuthCommand(@NotNull GitCommandLine cmd) {
      super(cmd);
    }

    @NotNull
    ExecResult run() throws VcsException {
      // straight to doRunCmd: the retry loop of runCmd is not a part of the error reporting under test
      return doRunCmd(getCmd(), new byte[0]);
    }
  }
}
