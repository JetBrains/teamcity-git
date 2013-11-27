package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.AskPassGenerator;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.FetchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.errors.GitIndexCorruptedException;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static org.testng.AssertJUnit.fail;

@Test
public class FetchCommandImplTest {

  @TestFor(issues = "TW-18853")
  public void should_throw_special_exception_when_stderr_mentions_broken_index() throws VcsException {
    AskPassGenerator fakeGen = new AskPassGenerator() {
      @NotNull
      public File generate(@NotNull AuthSettings authSettings) throws IOException {
        return new File(".");
      }
    };

    File tmpDir = new File(FileUtil.getTempDirectory());
    GitCommandLine failedCmd = new GitCommandLine(null, fakeGen, tmpDir, false) {
      @Override
      public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
        throw new VcsException("fatal: index file smaller than expected");
      }
    };

    FetchCommand fetch = new FetchCommandImpl(failedCmd)
      .setRefspec("+refs/heads/*:refs/remotes/origin/*")
      .setTimeout(3600)
      .setAuthSettings(new AuthSettings(new HashMap<String, String>()));

    try {
      fetch.call();
    } catch (GitIndexCorruptedException e) {
      //expected
    } catch (VcsException e) {
      fail("GitIndexCorruptedException should be thrown");
    }
  }

}
