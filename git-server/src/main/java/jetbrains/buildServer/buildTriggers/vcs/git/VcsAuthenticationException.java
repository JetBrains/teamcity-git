

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;

/**
 * Exception is thrown when some authentication problem occurred while processing vcs request.
 * @author dmitry.neverov
 */
public class VcsAuthenticationException extends VcsException {

  public VcsAuthenticationException(String message) {
    super(message);
  }

  public VcsAuthenticationException(String repositoryUrl, String message) {
    this(formatErrorMessage(repositoryUrl, message));
  }

  public VcsAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }

  public VcsAuthenticationException(String repositoryUrl, String message, Throwable e) {
    this(formatErrorMessage(repositoryUrl, message), e);
  }

  private static String formatErrorMessage(String repositoryUrl, String message) {
    return String.format("Repository '%s': %s", repositoryUrl, message);
  }
}