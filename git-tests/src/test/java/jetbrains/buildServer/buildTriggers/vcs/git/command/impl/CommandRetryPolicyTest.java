package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCommandRetryPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.vcs.VcsException;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

@Test
public class CommandRetryPolicyTest extends BaseTestCase {

  public void queue_draining_checkout_error_is_retryable() throws Exception {
    FetchCommandImpl fetch = new FetchCommandImpl(createCommandLine());
    fetch.setAuthSettings(getEmptyAuthSettings());

    BaseAuthCommandImpl<FetchCommand>.GitCommandRetryable retryable = fetch.getRetryable(createCommandLine(), new byte[0]);
    GitCommandRetryPolicy retryPolicy = retryable.findRetryPolicyForException(new VcsException(queueDrainingError()), 1, 3);

    Assertions.assertThat(retryPolicy.getMode()).isEqualTo(GitCommandRetryPolicy.RetryMode.DEFAULT);
  }

  public void queue_draining_checkout_error_is_not_retryable_after_last_attempt() throws Exception {
    FetchCommandImpl fetch = new FetchCommandImpl(createCommandLine());
    fetch.setAuthSettings(getEmptyAuthSettings());

    BaseAuthCommandImpl<FetchCommand>.GitCommandRetryable retryable = fetch.getRetryable(createCommandLine(), new byte[0]);
    GitCommandRetryPolicy retryPolicy = retryable.findRetryPolicyForException(new VcsException(queueDrainingError()), 3, 3);

    Assertions.assertThat(retryPolicy.getMode()).isEqualTo(GitCommandRetryPolicy.RetryMode.NOT_REQUIRED);
  }

  @NotNull
  private GitCommandLine createCommandLine() throws IOException {
    return new GitCommandLine(new StubContext("git", new GitVersion(2, 54, 0)), getFakeGen());
  }

  @NotNull
  private AuthSettings getEmptyAuthSettings() {
    return new AuthSettingsImpl(new HashMap<String, String>(), new URIishHelperImpl());
  }

  @NotNull
  private String queueDrainingError() {
    return "fatal: Queue is draining [request #ssh-0fd5-275]\n" +
           "fatal: Could not read from remote repository.\n";
  }

  @NotNull
  private ScriptGen getFakeGen() throws IOException {
    return new ScriptGen(createTempDir()) {
      @NotNull
      @Override
      public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
        return createTempFile();
      }

      @NotNull
      @Override
      public File generateAskPass(@NotNull String password) throws IOException {
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
