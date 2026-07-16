

package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.PackRefs;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.PackRefsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class MaintenanceOperationsTest extends BaseTestCase {

  @BeforeMethod
  public void setUp() throws Exception {
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
  }

  @NotNull
  private static String getGitPath() {
    String gitPath = System.getenv("TEAMCITY_GIT_PATH");
    if (gitPath == null) {
      gitPath = SystemInfo.isWindows ? "git.exe" : "git";
    }

    if (doRunCommand(true, gitPath, new File("."), "version") == null) return gitPath;
    throw new SkipException("No git executable provided, please specify TEAMCITY_GIT_PATH env variable");
  }

  @Nullable
  private static String doRunCommand(boolean failOnStdErr, @NotNull String executable, @NotNull File workingDir, String... params) {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath(executable);
    cmd.setWorkDirectory(workingDir.getAbsolutePath());
    Arrays.asList(params).forEach(p -> cmd.addParameter(p));

    final ExecResult res = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    if (res.getExitCode() == 0 && (!failOnStdErr || res.getStderr().isEmpty())) {
      return null;
    }
    return (res.getStderr().isEmpty() ? "" : res.getStderr()) + (res.getExitCode() == 0 ? "" : "(exit code: " + res.getExitCode() + ")");
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

  /**
   * The test uses a repository from the TW-100479 test data and then creates a "packed-refs.new" file inside it to
   * emulate another process being in the middle of packing refs (or a previous packing that was interrupted and left
   * the stale lock file behind).
   * The test verifies that:
   *  1. with the default settings "git pack-refs --all" fails because "packed-refs.new" already exists,
   *  2. with setErrorExpected(true) the pack-refs command tolerates the non-zero exit code caused by that conflict and
   *     does not throw,
   *  3. after GitUtils.removePackedRefsNew() removes the stale "packed-refs.new", "git pack-refs --all" succeeds again.
   */
  @Test
  public void pack_refs_interrupted_maintenance_test() throws Exception {
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");

    final File clone = createTempDir();
    final File remote = GitTestUtil.dataFile("TW-100479/remote/commit_graph_test_repo");
    FileUtil.copyDir(remote, clone);
    FileUtil.rename(new File(clone, "_git1"), new File(clone, ".git"));

    File gitDir = new File(clone, ".git");
    FileUtil.writeFileAndReportErrors(new File(gitDir, "packed-refs.new"), "1");

    {
      final GitCommandLine cmd1 = new GitCommandLine(new StubContext("git", version), getFakeGen());
      cmd1.setExePath(gitPath);
      cmd1.setWorkingDirectory(clone);
      PackRefs packRefsCommand = new PackRefsImpl(cmd1);
      assertExceptionThrown(() -> packRefsCommand.call(), VcsException.class,
                            e -> assertContains(e.getMessage(), "unable to write new packed-refs"));
    }

    {
      final GitCommandLine cmd2 = new GitCommandLine(new StubContext("git", version), getFakeGen());
      cmd2.setExePath(gitPath);
      cmd2.setWorkingDirectory(clone);
      PackRefs packRefsCommand2 = new PackRefsImpl(cmd2).setErrorExpected(true);
      try {
        packRefsCommand2.call();
      } catch (VcsException e) {
        fail("pack-refs should not throw an exception, but it did: " + e.getMessage());
      }
    }

    GitUtils.removePackedRefsNew(gitDir);

    {
      final GitCommandLine cmd3 = new GitCommandLine(new StubContext("git", version), getFakeGen());
      cmd3.setExePath(gitPath);
      cmd3.setWorkingDirectory(clone);
      PackRefs packRefsCommand3 = new PackRefsImpl(cmd3);
      try {
        packRefsCommand3.call();
      } catch (VcsException e) {
        fail("pack-refs should not throw an exception, but it did: " + e.getMessage());
      }
    }
  }
}
