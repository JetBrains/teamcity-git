package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.HashMap;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.UpdaterImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.FetchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND;

/**
 * Checks that {@link UpdaterImpl#BRANCH_COLLISION_ERROR_PATTERN} recognizes the error real git prints on a ref name conflict.
 *
 * @deprecated temporary class: move the test and {@code fetchAllBranches} to {@link FetchCommandImplTest}, which already has the git helpers,
 * then delete this class and its line in native-git-testng.xml.
 */
@Deprecated
@Test
public class BranchCollisionFetchTest extends BaseTestCase {

  @Override
  @BeforeMethod(alwaysRun = true)
  protected void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
  }

  /**
   * The remote replaces feature-a with feature-a/x, so the local ref refs/remotes/origin/feature-a blocks the new one.
   * Since git 2.51 fetch reports it only as "some local refs could not be updated", without the conflicting ref name.
   */
  public void fetch_error_on_branch_collision_matches_updater_pattern() throws Exception {
    setInternalProperty(NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND, "false");
    String gitPath = getGitPath();
    GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();

    File remote = createTempDir();
    FileUtil.copyDir(GitTestUtil.dataFile("TW-100479/remote/commit_graph_test_repo"), remote);
    FileUtil.rename(new File(remote, "_git1"), new File(remote, ".git"));

    File work = createTempDir();
    runGit(gitPath, work, "init");
    fetchAllBranches(gitPath, version, work, remote);

    runGit(gitPath, remote, "branch", "-D", "feature-a");
    runGit(gitPath, remote, "branch", "feature-a/x", "main");

    try {
      fetchAllBranches(gitPath, version, work, remote);
      fail("Fetch must fail on the branch collision");
    } catch (VcsException e) {
      assertTrue(e.getMessage(), UpdaterImpl.BRANCH_COLLISION_ERROR_PATTERN.matcher(e.getMessage()).find());
    }
  }

  private static void fetchAllBranches(@NotNull String gitPath, @NotNull GitVersion version, @NotNull File work, @NotNull File remote) throws VcsException {
    GitCommandLine cmd = new GitCommandLine(new StubContext(gitPath, version), new AgentGitFacadeImpl(gitPath).getScriptGen());
    cmd.setExePath(gitPath);
    cmd.setWorkingDirectory(work);
    FetchCommand fetch = new FetchCommandImpl(cmd);
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(new AuthSettingsImpl(new HashMap<>(), new URIishHelperImpl()));
    fetch.setRefspec("+refs/heads/*:refs/remotes/origin/*");
    fetch.call();
  }

  @NotNull
  private static String getGitPath() {
    String gitPath = System.getenv("TEAMCITY_GIT_PATH");
    if (gitPath == null) {
      gitPath = SystemInfo.isWindows ? "git.exe" : "git";
    }
    if (runGitCommand(gitPath, new File("."), "version").getExitCode() != 0) {
      throw new SkipException("No git executable provided, please specify TEAMCITY_GIT_PATH env variable");
    }
    return gitPath;
  }

  private static void runGit(@NotNull String gitPath, @NotNull File workingDir, @NotNull String... params) {
    ExecResult result = runGitCommand(gitPath, workingDir, params);
    if (result.getExitCode() != 0) {
      throw new RuntimeException(result.getStderr() + "(exit code: " + result.getExitCode() + ")");
    }
  }

  @NotNull
  private static ExecResult runGitCommand(@NotNull String gitPath, @NotNull File workingDir, @NotNull String... params) {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath(gitPath);
    cmd.setWorkDirectory(workingDir.getAbsolutePath());
    cmd.addParameters(params);
    return SimpleCommandLineProcessRunner.runCommand(cmd, null);
  }
}
