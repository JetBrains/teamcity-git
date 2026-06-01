

package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.FetchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.LsRemoteCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.RefImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.FetchSettingsFactoryImplTest;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.util.FileUtil.getTempDirectory;

@Test
public class FetchCommandImplTest extends BaseTestCase {

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
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

  private static void runCommand(boolean failOnStdErr, @NotNull String executable, @NotNull File workingDir, String... params) {
    final String res = doRunCommand(failOnStdErr, executable, workingDir, params);
    if (res == null) return;
    throw new RuntimeException(res);
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

  @TestFor(issues = "TW-18853")
  public void should_throw_special_exception_when_stderr_mentions_broken_index() throws Exception {

    AgentGitCommandLine failedCmd = new AgentGitCommandLine(null, getFakeGen(), new StubContext()) {
      @Override
      public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
        throw new VcsException("fatal: index file smaller than expected");
      }
    };

    FetchCommand fetch = new FetchCommandImpl(failedCmd)
      .setRefspec("+refs/heads/*:refs/remotes/origin/*")
      .setTimeout(3600)
      .setAuthSettings(getEmptyAuthSettings());

    try {
      fetch.call();
    } catch (GitIndexCorruptedException e) {
      //expected
    } catch (VcsException e) {
      fail("GitIndexCorruptedException should be thrown");
    }
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

  @NotNull
  private static GitProgressLogger createLogger(@NotNull StringBuilder sb) {
    return new GitProgressLogger() {
      @Override
      public void openBlock(@NotNull String name) {
        message(name);
      }

      @Override
      public void message(@NotNull String message) {
        sb.append(message);
      }

      @Override
      public void warning(@NotNull String message) {
        message(message);
      }

      @Override
      public void progressMessage(@NotNull String message) {
        message(message);
      }

      @Override
      public void closeBlock(@NotNull String name) {
      }
    };
  }

  @Test
  public void fetch_multiple_refspecs() throws Exception {
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");

    final File remote = GitTestUtil.dataFile("fetch_multiple_refspecs");
    new File(remote, "refs" + File.separator + "heads").mkdirs();

    final File work = createTempDir();
    runCommand(false, gitPath, work, "init");

    final GitCommandLine cmd = new GitCommandLine(new StubContext("git", version), getFakeGen());
    cmd.setExePath(gitPath);
    cmd.setWorkingDirectory(work);
    final FetchCommandImpl fetch = new FetchCommandImpl(cmd);
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());

    for (int i = 0; i < 6000; ++i) {
      fetch.setRefspec("+refs/heads/branch" + i + ":refs/remotes/origin/branch" + i);
    }

    fetch.call();
    File branchObjects = new File(work, ".git/refs/remotes/origin");
    if (branchObjects.exists()) {
      assertEquals(6000, FileUtil.listFiles(branchObjects, (d, n) -> true).length);
    } else {
      File packedRefs = new File(work, ".git/packed-refs");
      assertTrue(packedRefs.exists());
      assertEquals(6000, FileUtil.readFile(packedRefs).stream().filter(s -> s.contains("refs/remotes/origin/")).count());
    }
  }

  @Test
  public void fetch_with_disappeared_refspecs() throws Exception {
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");

    final File remote = GitTestUtil.dataFile("fetch_multiple_refspecs");
    new File(remote, "refs" + File.separator + "heads").mkdirs();

    final File work = createTempDir();
    runCommand(false, gitPath, work, "init");

    final GitCommandLine cmd = new GitCommandLine(new StubContext("git", version), getFakeGen());
    cmd.setExePath(gitPath);
    cmd.setWorkingDirectory(work);
    final FetchCommandImpl fetch = new FetchCommandImpl(cmd);
    AtomicBoolean wasLsRemoteExecuted = new AtomicBoolean(false);
    fetch.setRefSpecsRefresher(() -> new LsRemoteCommandImpl(cmd) {
      @NotNull
      @Override
      public List<Ref> call() throws VcsException {
        wasLsRemoteExecuted.set(true);
        return IntStream.rangeClosed(0, 5999).boxed().map(i -> new RefImpl("refs/heads/branch" + i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
      }
    }.call());
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());

    for (int i = 0; i < 6000; ++i) {
      fetch.setRefspec("+refs/heads/branch" + i + ":refs/heads/branch" + i);
    }

    for (int i = 0; i < 10; ++i) {
      fetch.setRefspec("+refs/heads/disappear_branch" + i + ":refs/heads/disappear_branch" + i);
    }

    fetch.call();

    assertTrue(wasLsRemoteExecuted.get());
    File heads = new File(work, ".git/refs/heads");
    if (heads.exists() && !FileUtil.isEmptyDir(heads)) {
      assertEquals(6000, FileUtil.listFiles(heads, (d, n) -> true).length);
    } else {
      File packedRefs = new File(work, ".git/packed-refs");
      assertTrue(packedRefs.exists());
      assertEquals(6000, FileUtil.readFile(packedRefs).stream().filter(s -> s.contains("refs/heads")).count());
    }
  }

  /*
  @Test
  public void fetch_with_renamed_refspec() throws Exception {
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");

    final File remote = GitTestUtil.dataFile("fetch_multiple_refspecs");
    new File(remote, "refs" + File.separator + "heads").mkdirs();


    final File remoteCopy = new File(getTempDirectory(), "fetch_multiple_refspecs");
    FileUtil.delete(remoteCopy);
    copyDir(dataFile("fetch_multiple_refspecs"), remoteCopy);


    final File work = createTempDir();
    runCommand(false, gitPath, work, "--bare", "init");

    final GitCommandLine cmd1 = new GitCommandLine(new StubContext("git", version), getFakeGen());
    cmd1.setExePath(gitPath);
    cmd1.setWorkingDirectory(work);

    final FetchCommandImpl preliminaryFetch = new FetchCommandImpl(cmd1);
    preliminaryFetch.setRemote(remoteCopy.getAbsolutePath());
    preliminaryFetch.setAuthSettings(getEmptyAuthSettings());

    for (int i = 0; i < 30; ++i) {
      preliminaryFetch.setRefspec("+refs/heads/branch" + i + ":refs/heads/branch" + i);
    }

    preliminaryFetch.call();


    runCommand(false, gitPath, remoteCopy, "branch", "-D", "branch1");
    runCommand(false, gitPath, remoteCopy, "branch", "branch1/new");


    final GitCommandLine cmd2 = new GitCommandLine(new StubContext("git", version), getFakeGen());
    cmd2.setExePath(gitPath);
    cmd2.setWorkingDirectory(work);
    final FetchCommandImpl fetch = new FetchCommandImpl(cmd2);
    fetch.setRefSpecsRefresher(() -> new LsRemoteCommandImpl(cmd2) {
      @NotNull
      @Override
      public List<Ref> call() throws VcsException {
        runCommand(false, gitPath, work, "remote", "prune", "origin");
        List<Ref> refs = IntStream.rangeClosed(3, 5999).boxed().map(i -> new RefImpl("refs/heads/branch" + i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
        refs.add(new RefImpl("refs/heads/branch1/new", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        return refs;
      }
    }.call());
    fetch.setRemote(remoteCopy.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());



    //assume that branch1 was renamed to branch1/new between ls-remote and fetch, but ref to branch1 is saved in the local repository
    runCommand(false, gitPath, work, "remote", "prune", remoteCopy.getAbsolutePath());

    fetch.setRefspec("+refs/*:refs/*");


    fetch.call();


    File heads = new File(work, ".git/refs/heads");
    if (heads.exists() && !FileUtil.isEmptyDir(heads)) {
      assertEquals(30, FileUtil.listFiles(heads, (d, n) -> true).length);
    } else {
      File packedRefs = new File(work, ".git/packed-refs");
      assertTrue(packedRefs.exists());
      assertEquals(30, FileUtil.readFile(packedRefs).stream().filter(s -> s.contains("refs/heads")).count());
    }
  }
*/
  @Test
  public void do_fetch_test() throws Exception {
    // init and clone repo
    // create branch which will be outdated locally
    // do doFetch with specs
    // do doFetch with wildcard


    // todo move to setup
    TempFiles myTemp = new TempFiles();
    FetchSettingsFactoryImpl myFactory = new FetchSettingsFactoryImpl();
    PluginConfigBuilder myPluginConfigBuilder = new PluginConfigBuilder(new ServerPaths(myTemp.createTempDir().getAbsolutePath()));
    GitVcsSupport git = gitSupport().withPluginConfig(myPluginConfigBuilder).build();
    OperationContext myContext = git.createContext(VcsRootBuilder.vcsRoot().withFetchUrl("https://mygit.com/owner/repo").build(), "fetch");

    FetchSettings mySettings = myFactory.getFetchSettings(myContext,
                                                          Arrays.asList(commit("refs/heads/main", "a"), commit("branch1", "b"), commit("branch2", "c"), commit("branch3", "c")),
                                                          Arrays.asList(commit("refs/heads/main", "a"), commit("branch1", "b"), commit("branch2", "c"), commit("branch3", "c")),
                                                          new HashSet<String>(Arrays.asList("refs/heads/main", "refs/heads/branch1", "refs/heads/branch2", "refs/heads/branch3")),
                                                          true);


  }



  @Test
  public void fetch_with_disappeared_refspecs_fail() throws Exception {
    setInternalProperty(NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND, "false");
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");

    final File remote = GitTestUtil.dataFile("fetch_multiple_refspecs");
    new File(remote, "refs" + File.separator + "heads").mkdirs();

    final File work = createTempDir();
    runCommand(false, gitPath, work, "init");

    final GitCommandLine cmd = new GitCommandLine(new StubContext("git", version), getFakeGen());
    cmd.setExePath(gitPath);
    cmd.setWorkingDirectory(work);
    final FetchCommandImpl fetch = new FetchCommandImpl(cmd);
    fetch.setRefSpecsRefresher(() -> new LsRemoteCommandImpl(cmd) {
      @NotNull
      @Override
      public List<Ref> call() throws VcsException {
        return IntStream.rangeClosed(0, 5999).boxed().map(i -> new RefImpl("refs/heads/branch" + i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
      }
    }.call());
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());

    for (int i = 0; i < 6000; ++i) {
      fetch.setRefspec("+refs/heads/branch" + i + ":refs/heads/branch" + i);
    }

    for (int i = 0; i < 10; ++i) {
      fetch.setRefspec("+refs/heads/disappear_branch" + i + ":refs/heads/disappear_branch" + i);
    }

    assertExceptionThrown(() -> fetch.call(), VcsException.class);
  }

  @Test
  public void negative_refspec_for_tags() throws Exception {
    final String gitPath = getGitPath();
    final GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
    if (!GitVersion.negativeRefSpecSupported(version)) throw new SkipException("Git version is too old to run this test");

    final File remote = GitTestUtil.dataFile("fetch_multiple_refspecs");
    new File(remote, "refs" + File.separator + "heads").mkdirs();

    final File work = createTempDir();
    runCommand(false, gitPath, work, "init", "--bare");

    final StringBuilder log = new StringBuilder();
    final StubContext context = new StubContext("git", version);
    context.setLogger(createLogger(log));

    final GitCommandLine cmd = new GitCommandLine(context, getFakeGen());
    cmd.setExePath(gitPath);
    cmd.setWorkingDirectory(work);
    final FetchCommandImpl fetch = new FetchCommandImpl(cmd);
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());

    fetch.setRefspec("+refs/*:refs/*");
    fetch.setRefspec("^refs/tags/*");
    fetch.setFetchTags(false);

    fetch.call();

    // master + 6000 branches
    File headsRefsDir = new File(work, "refs/heads");
    File packedRefs = new File(work, "packed-refs");
    List<String> packedRefsLines = packedRefs.exists() ? FileUtil.readFile(packedRefs) : Arrays.asList();

    if (FileUtil.isEmptyDir(headsRefsDir)) {
      assertTrue(packedRefs.exists());
      assertEquals(6001, packedRefsLines.stream().filter(s -> s.contains("refs/heads/")).count());
      assertFalse(packedRefsLines.contains("refs/tags/my_tag"));
    } else {
      assertEquals(6001, FileUtil.listFiles(new File(work, "refs/heads"), (d, n) -> true).length);
    }
    assertFalse(new File(work, "refs/tags/my_tag").exists());
    if(packedRefs.exists()) {
      assertFalse(packedRefsLines.stream().anyMatch(r-> r.contains("refs/tags/my_tag")));
    }

    final String logStr = log.toString();
    assertFalse(logStr.contains("my_tag"));
  }

  private RefCommit commit(@NotNull String ref, @NotNull String commit) {
    return new FetchSettingsFactoryImplTest.MockRefCommit(ref, commit, true);
  }
}