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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import jetbrains.buildServer.NetworkUtil;
import jetbrains.buildServer.agent.ssl.TrustedCertificatesDirectory;
import jetbrains.buildServer.util.FileUtil;

import javax.net.ssl.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;

class SSLTestUtil {

  static final char[] KEY_STORE_PASSWD = "123456".toCharArray();
  static final String KEY_STORE =
    "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";

  static final String CERT_PUBLIC = "-----BEGIN CERTIFICATE-----\n" +
                                           "MIICQTCCAaoCCQCgsSqblM1uHDANBgkqhkiG9w0BAQUFADBlMQswCQYDVQQGEwJD\n" +
                                           "TjELMAkGA1UECAwCQ1MxCzAJBgNVBAcMAkxOMQswCQYDVQQKDAJPTjEMMAoGA1UE\n" +
                                           "CwwDT1VOMQswCQYDVQQDDAJDTjEUMBIGCSqGSIb3DQEJARYFYW1haWwwHhcNMTgw\n" +
                                           "NzI1MDc0MTQyWhcNMTkwNzI1MDc0MTQyWjBlMQswCQYDVQQGEwJDTjELMAkGA1UE\n" +
                                           "CAwCQ1MxCzAJBgNVBAcMAkxOMQswCQYDVQQKDAJPTjEMMAoGA1UECwwDT1VOMQsw\n" +
                                           "CQYDVQQDDAJDTjEUMBIGCSqGSIb3DQEJARYFYW1haWwwgZ8wDQYJKoZIhvcNAQEB\n" +
                                           "BQADgY0AMIGJAoGBALd6XvMOgLUrjioJxgudKQlcqbPbihcWWha1SvfY491Ya93Q\n" +
                                           "q3R8AiLybJqidfdlDZFA/fiXsIs+LnQD9S+uFdC83u2gpzqlIim7A7w/X4B8JClP\n" +
                                           "wNS8AebnAcn8FEgi9AOHsoBb/mitke6gUkf5TUAwsdsTDi2YV7Rdmy1Ux6GVAgMB\n" +
                                           "AAEwDQYJKoZIhvcNAQEFBQADgYEAPPh1TupBgST6RVyWvJvXlcnPm3LOH3J8Jd3V\n" +
                                           "+bm6+W4zs1TLjZgOzGLoTR05INISahYDjAlYZm2v0aOYm2MdxZepxSec/47K4HL2\n" +
                                           "gr3hMGf7xFwTNLwxNmiTiBneuTcxfinGxAp+grq9jMaZXGWKorS1ATnyWmpXfQ8j\n" +
                                           "ESLNmVw=\n" +
                                           "-----END CERTIFICATE-----";

  private KeyStore myServerKeyStore;
  private Certificate myServerCertificate;
  private HttpsServer myHttpsServer;
  private int myServerPort = 0;

  File writeAnotherCert(final File homeDirectory) throws Exception {
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDirectory.getPath());
    final File cert = new File(certDirectory, "cert.pem");
    cert.getParentFile().mkdirs();
    FileUtil.writeFile(cert, CERT_PUBLIC, "UTF-8");
    return cert;
  }

  File writeServerCert(final File homeDirectory) throws Exception {
    final String certDirectory = TrustedCertificatesDirectory.getAllCertificatesDirectoryFromHome(homeDirectory.getPath());
    final File cert = new File(certDirectory, "cert.pem");
    cert.getParentFile().mkdirs();
    FileUtil.writeToFile(cert, getServerCertificate().getEncoded());
    return cert;
  }

  KeyStore getServerKeyStore() throws Exception {
    if (myServerKeyStore != null) {
      return myServerKeyStore;
    }
    final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    final byte[] keyStoreData = Base64.getDecoder().decode(SSLTestUtil.KEY_STORE);
    final char[] password = "123456".toCharArray();
    try (final ByteInputStream in = new ByteInputStream(keyStoreData, keyStoreData.length)) {
      keyStore.load(in, password);
    }
    myServerKeyStore = keyStore;
    return myServerKeyStore;
  }

  Certificate getServerCertificate() throws Exception {
    if (myServerCertificate != null) {
      return myServerCertificate;
    }
    myServerCertificate = getServerKeyStore().getCertificate("git_plugin");
    return myServerCertificate;
  }

  HttpsServer getHttpsServer() throws Exception {
    if (myHttpsServer != null) {
      return myHttpsServer;
    }
    final int freePort = getServerPort();
    final HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", freePort), 0);
    final SSLContext sslContext = SSLContext.getInstance("TLS");

    final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(getServerKeyStore(), KEY_STORE_PASSWD);
    // setup the trust manager factory
    final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(getServerKeyStore());
    // setup the HTTPS context and parameters
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
      public void configure(HttpsParameters params) {
        try {
          // initialise the SSL context
          SSLContext c = SSLContext.getDefault();
          SSLEngine engine = c.createSSLEngine();
          params.setNeedClientAuth(false);
          params.setCipherSuites(engine.getEnabledCipherSuites());
          params.setProtocols(engine.getEnabledProtocols());

          // get the default parameters
          SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
          params.setSSLParameters(defaultSSLParameters);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    myHttpsServer = server;
    return myHttpsServer;
  }

  public int getServerPort() {
    if (myServerPort != 0) {
      return myServerPort;
    }
    myServerPort = NetworkUtil.getFreePort(1025);
    return myServerPort;
  }
}
