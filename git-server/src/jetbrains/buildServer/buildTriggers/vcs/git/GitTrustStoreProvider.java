

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.security.KeyStore;

/**
 * Abstract provider of trust store for ssl connections.
 *
 * @author Mikhail Khorkov
 * @since tc-2018.1
 */
public interface GitTrustStoreProvider {

  /**
   * Returns trust store or <code>null</code>.
   */
  @Nullable
  KeyStore getTrustStore();

  /**
   * @return directory where trusted certificates are located or null if this directory is unknown
   */
  @Nullable
  File getTrustedCertificatesDir();
}