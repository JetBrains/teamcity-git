

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.security.KeyStore;

/**
 * Implementation of {@link GitTrustStoreProvider} for BuildServer.
 *
 * @author Mikhail Khorkov
 * @since tc-2018.1
 */
public class GitTrustStoreProviderImpl implements GitTrustStoreProvider {

  @NotNull
  private final SSLTrustStoreProvider mySSLTrustStoreProvider;
  private volatile File myTrustedCertificatesDir;

  public GitTrustStoreProviderImpl(@NotNull final SSLTrustStoreProvider sslTrustStoreProvider) {
    mySSLTrustStoreProvider = sslTrustStoreProvider;
  }

  public void setTrustedCertificatesDir(@Nullable final File trustedCertificatesDir) {
    myTrustedCertificatesDir = trustedCertificatesDir;
  }

  @Nullable
  public File getTrustedCertificatesDir() {
    return myTrustedCertificatesDir;
  }

  @Nullable
  @Override
  public KeyStore getTrustStore() {
    return mySSLTrustStoreProvider.getTrustStore();
  }
}