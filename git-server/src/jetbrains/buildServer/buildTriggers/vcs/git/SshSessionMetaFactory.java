package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public interface SshSessionMetaFactory {
  /**
   * Get appropriate session factory object for specified settings and url
   *
   * @param url URL of interest
   * @param authSettings a vcs root settings
   * @return session factory object
   * @throws VcsException in case of problems with creating object
   */
  @NotNull
  SshSessionFactory getSshSessionFactory(@NotNull URIish url, @NotNull AuthSettings authSettings) throws VcsException;
}
