

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TrustedCertificatesDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class TrustedCertificatesInitializer {
  public TrustedCertificatesInitializer(@NotNull ServerPaths serverPaths, @NotNull GitTrustStoreProviderImpl gitTrustStoreProvider) {
    String path = TrustedCertificatesDirectory.getCertificateDirectoryForProject(serverPaths.getProjectsDir().getPath(), SProject.ROOT_PROJECT_ID);
    gitTrustStoreProvider.setTrustedCertificatesDir(new File(path));
  }
}