package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

public class SshKeyNotFoundException extends RuntimeException {
  public SshKeyNotFoundException(String message) {
    super(message);
  }
}
