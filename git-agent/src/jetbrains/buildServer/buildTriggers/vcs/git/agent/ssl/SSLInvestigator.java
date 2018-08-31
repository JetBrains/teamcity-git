/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl;

import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitFacade;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.apache.commons.codec.CharEncoding;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.*;

/**
 * Component for investigate either we need use custom ssl certificates for git fetch or not.
 *
 * @author Mikhail Khorkov
 * @since 2018.1.2
 */
public class SSLInvestigator {

  private final static Logger LOG = Logger.getLogger(SSLInvestigator.class);

  private final static String CERT_FILE = "git_custom_certificates.crt";

  private final URIish myFetchURL;
  private final String myTempDirectory;
  private final String myHomeDirectory;
  private final SSLChecker mySSLChecker;
  private final SSLContextRetriever mySSLContextRetriever;

  private volatile Boolean myNeedCustomCertificate = null;
  private volatile String myCAInfoPath = null;

  public SSLInvestigator(@NotNull final URIish fetchURL, @NotNull final String tempDirectory, @NotNull final String homeDirectory) {
    this(fetchURL, tempDirectory, homeDirectory, new SSLCheckerImpl(), new SSLContextRetrieverImpl());
  }

  public SSLInvestigator(@NotNull final URIish fetchURL, @NotNull final String tempDirectory, @NotNull final String homeDirectory,
                         @NotNull final SSLChecker sslChecker, @NotNull final SSLContextRetriever sslContextRetriever) {
    myFetchURL = fetchURL;
    myTempDirectory = tempDirectory;
    myHomeDirectory = homeDirectory;
    mySSLChecker = sslChecker;
    mySSLContextRetriever = sslContextRetriever;

    if (!"https".equals(myFetchURL.getScheme())) {
      myNeedCustomCertificate = false;
    }
    if (!TeamCityProperties.getBooleanOrTrue("teamcity.ssl.useCustomTrustStore.git")) {
      myNeedCustomCertificate = false;
    }
  }

  public void setCertificateOptions(@NotNull final GitFacade gitFacade) {
    if (!isNeedCustomCertificates()) {
      deleteSslOption(gitFacade);
      return;
    }

    final String caInfoPath = caInfoPath();
    if (caInfoPath != null) {
      setSslOption(gitFacade, caInfoPath);
    }
  }

  @Nullable
  private String caInfoPath() {
    String caInfoPath = myCAInfoPath;
    if (caInfoPath == null) {
      synchronized (this) {
        caInfoPath = myCAInfoPath;
        if (caInfoPath != null) {
          return caInfoPath;
        }

        caInfoPath = generateCertificateFile();
        myCAInfoPath = caInfoPath;
      }
    }
    return caInfoPath;
  }

  @Nullable
  private String generateCertificateFile() {
    try {
      final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(myHomeDirectory);
      final String pemContent = TrustStoreIO.pemContentFromDirectory(certDirectory);
      if (!pemContent.isEmpty()) {
        final File file = new File(myTempDirectory, CERT_FILE);
        FileUtil.writeFile(file, pemContent, CharEncoding.UTF_8);
        return file.getPath();
      }
    } catch (IOException e) {
      LOG.error("Can not write file with certificates", e);
    }
    return null;
  }

  private boolean isNeedCustomCertificates() {
    Boolean need = myNeedCustomCertificate;
    if (need == null) {
      synchronized (this) {
        need = myNeedCustomCertificate;
        if (need != null) {
          return need;
        }

        need = doesCanConnectWithCustomCertificate();
        myNeedCustomCertificate = need;
      }
    }
    return need;
  }

  private boolean doesCanConnectWithCustomCertificate() {
    try {
      final SSLContext sslContext = mySSLContextRetriever.retrieve(myHomeDirectory);
      if (sslContext == null) {
        /* there are no custom certificate */
        return false;
      }

      final int port = myFetchURL.getPort() > 0 ? myFetchURL.getPort() : 443;
      return mySSLChecker.canConnect(sslContext, myFetchURL.getHost(), port);

    } catch (Exception e) {
      LOG.error("Unexpected error while try to connect to git server " + myFetchURL.toString()
                + " for checking custom certificates", e);
      /* unexpected error. do not use custom certificate then */
      return false;
    }
  }

  private void deleteSslOption(@NotNull final GitFacade gitFacade) {
    try {
      final String previous = gitFacade.getConfig().setPropertyName("http.sslCAInfo").call();
      if (!StringUtil.isEmptyOrSpaces(previous)) {
        /* do not need custom certificate then remove corresponding options if exists */
        gitFacade.setConfig().setPropertyName("http.sslCAInfo").unSet().call();
      }
    } catch (Exception e) {
      /* option was not exist, ignore exception then */
    }
  }

  private void setSslOption(@NotNull final GitFacade gitFacade, @NotNull final String path) {
    try {
      gitFacade.setConfig().setPropertyName("http.sslCAInfo").setValue(path).call();
    } catch (Exception e) {
      LOG.error("Error while setting sslCAInfo git option");
    }
  }

  public interface SSLContextRetriever {
    @Nullable
    SSLContext retrieve(@NotNull String homeDirectory) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException;
  }

  public static class SSLContextRetrieverImpl implements SSLContextRetriever {

    @Override
    @Nullable
    public SSLContext retrieve(@NotNull final String homeDirectory)
      throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
      final X509TrustManager manager = trustManager(homeDirectory);
      if (manager == null) {
        return null;
      }

      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[]{manager}, new SecureRandom());

      return context;
    }

    @Nullable
    private X509TrustManager trustManager(@NotNull final String homeDirectory) throws NoSuchAlgorithmException, KeyStoreException {
      final KeyStore trustStore = trustStore(homeDirectory);
      if (trustStore == null) {
        return null;
      }

      final TrustManagerFactory manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      manager.init(trustStore);

      return (X509TrustManager)manager.getTrustManagers()[0];
    }

    @Nullable
    private KeyStore trustStore(@NotNull final String homeDirectory) {
      final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDirectory);
      return TrustStoreIO.readTrustStoreFromDirectory(certDirectory);
    }
  }

  public interface SSLChecker {
    boolean canConnect(@NotNull final SSLContext sslContext, @NotNull final String host, int port) throws Exception;
  }

  public static class SSLCheckerImpl implements SSLChecker {
    @Override
    public boolean canConnect(@NotNull final SSLContext sslContext, @NotNull final String host, final int port) throws Exception {
      final SSLSocket socket = (SSLSocket)sslContext.getSocketFactory().createSocket(host, port);
      socket.setSoTimeout(TeamCityProperties.getInteger("teamcity.ssl.checkTimeout.git", 10 * 1000));
      try {
        socket.startHandshake();
        socket.close();
      } catch (Exception e) {
        /* can't connect with custom certificate */
        return false;
      }
      return true;
    }
  }
}
