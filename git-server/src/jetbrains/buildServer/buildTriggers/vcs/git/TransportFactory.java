

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.File;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public interface TransportFactory extends SshSessionMetaFactory {

  /**
   * Get a transport connection for specified local repository, URL and authentication settings
   *
   * @param r local repository to open
   * @param url URL to open
   * @param authSettings authentication settings
   * @return see above
   * @throws NotSupportedException if transport is not supported
   * @throws VcsException if there is a problem with configuring the transport
   */
  Transport createTransport(@NotNull Repository r, @NotNull final URIish url, @NotNull AuthSettings authSettings)
    throws NotSupportedException, VcsException, TransportException;

  Transport createTransport(@NotNull Repository r, @NotNull URIish url, @NotNull AuthSettings authSettings, int timeoutSeconds)
    throws NotSupportedException, VcsException, TransportException;

  @Nullable
  File getCertificatesDir();
}