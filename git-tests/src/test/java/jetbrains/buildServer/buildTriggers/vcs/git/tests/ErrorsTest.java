package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.Errors;
import jetbrains.buildServer.vcs.VcsException;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class ErrorsTest extends BaseTestCase {

  public void fatal_message_is_the_first_fatal_line_only() {
    String fatalLine = "Error fetching mirror repository git@github.com:JetBrains/teamcity-project-dsl.git: Short read of block. [request #ssh-7d42-257745]";
    VcsException exception = new VcsException(
      "git -c credential.helper= ls-remote origin command failed." +
      "\nexit code: 128" +
      "\nstderr: OpenSSH_8.7p1, OpenSSL 3.5.5 27 Jan 2026" +
      "\ndebug1: Sending command: git-upload-pack '/tc/TeamCity-BuildServer-Settings.git'" +
      "\ndebug2: channel 0: rcvd ext data 140" +
      "\nfatal: " + fatalLine +
      "\ndebug2: channel 0: written 140 to efd 6" +
      "\nfatal: Could not read from remote repository." +
      "\ndebug1: Exit status 42");

    then(Errors.getFatalMessage(exception)).isEqualTo(fatalLine);
  }

  public void no_fatal_message() {
    VcsException vcsException = new VcsException("git ls-remote origin command failed.\nexit code: 128\nstderr: some output");

    then(Errors.getFatalMessage(vcsException)).isNull();
  }

  public void fatal_message_of_a_single_line_error() {
    String error = "repository 'https://some.org/repo.git/' not found";

    then(Errors.getFatalMessage(new VcsException("fatal: " + error)))
      .isEqualTo(error);
  }

  public void remote_message_is_a_single_line_too() {
    String error = "Invalid username or password.";
    VcsException exception = new VcsException(
      "stderr: remote: " + error +
      "\nfatal: Authentication failed for 'https://some.org/repo.git/'");

    then(Errors.getRemoteMessage(exception))
      .isEqualTo(error);
  }
}
