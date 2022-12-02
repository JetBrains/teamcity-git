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

package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
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
import jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
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

import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.NATIVE_GIT_RETRY_IF_REMOTE_REF_NOT_FOUND;

@Test
public class FetchCommandImplTest extends BaseTestCase {

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
  public void should_throw_special_exception_when_stderr_mentions_broken_index() throws VcsException {

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
  private ScriptGen getFakeGen() {
    return new ScriptGen(new File(".")) {
      @NotNull
      public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
        return new File(".");
      }

      @NotNull
      @Override
      public File generateAskPass(@NotNull final String password) throws IOException {
        return new File(".");
      }

      @NotNull
      protected String getCredHelperTemplate() {
        return "";
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
    assertEquals(6000, FileUtil.listFiles(new File(work, ".git/refs/remotes/origin"), (d, n) -> true).length);
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
    fetch.setRefSpecsRefresher(new LsRemoteCommandImpl(cmd) {
      @NotNull
      @Override
      public List<Ref> call() throws VcsException {
        return IntStream.rangeClosed(0, 5999).boxed().map(i -> new RefImpl("refs/heads/branch" + i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
      }
    });
    fetch.setRemote(remote.getAbsolutePath());
    fetch.setAuthSettings(getEmptyAuthSettings());

    for (int i = 0; i < 6000; ++i) {
      fetch.setRefspec("+refs/heads/branch" + i + ":refs/heads/branch" + i);
    }

    for (int i = 0; i < 10; ++i) {
      fetch.setRefspec("+refs/heads/disappear_branch" + i + ":refs/heads/disappear_branch" + i);
    }

    fetch.call();
    assertEquals(6000, FileUtil.listFiles(new File(work, ".git/refs/heads"), (d, n) -> true).length);
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
    fetch.setRefSpecsRefresher(new LsRemoteCommandImpl(cmd) {
      @NotNull
      @Override
      public List<Ref> call() throws VcsException {
        return IntStream.rangeClosed(0, 5999).boxed().map(i -> new RefImpl("refs/heads/branch" + i, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
      }
    });
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
    assertEquals(6001, FileUtil.listFiles(new File(work, "refs/heads"), (d, n) -> true).length);
    assertFalse(new File(work, "refs/tags/my_tag").exists());
    final String logStr = log.toString();
    assertFalse(logStr.contains("my_tag"));
  }
}
