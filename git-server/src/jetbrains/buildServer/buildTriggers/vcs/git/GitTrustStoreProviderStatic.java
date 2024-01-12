

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.security.KeyStore;

/**
 * Implementation of {@link GitTrustStoreProvider} for static folder.
 *
 * @author Mikhail Khorkov
 * @since tc-2018.1
 */
public class GitTrustStoreProviderStatic implements GitTrustStoreProvider {

  @Nullable
  private final String myTrustedCertificatesDir;

  public GitTrustStoreProviderStatic(@Nullable final String trustedCertificatesDir) {
    myTrustedCertificatesDir = trustedCertificatesDir;
  }

  @Nullable
  @Override
  public KeyStore getTrustStore() {
    if (myTrustedCertificatesDir == null) {
      return null;
    } else {
      return TrustStoreIO.readTrustStoreFromDirectory(myTrustedCertificatesDir);
    }
  }

  @Nullable
  @Override
  public File getTrustedCertificatesDir() {
    return myTrustedCertificatesDir == null ? null : new File(myTrustedCertificatesDir);
  }
}