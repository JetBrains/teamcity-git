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

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyStore;
import java.util.function.Supplier;

public class SNIHttpClientConnectionFactory implements HttpConnectionFactory {

  private final Supplier<KeyStore> myTrustStoreGetter;
  private final SSLSchemePatcher mySSLSchemePatcher = (clientBuilder, sslContext, hostnameVerifier) -> {
    SSLConnectionSocketFactory cf = hostnameVerifier != null
                                    ? new SSLConnectionSocketFactory(sslContext, hostnameVerifier)
                                    : new SSLConnectionSocketFactory(sslContext);
    clientBuilder.setSSLSocketFactory(cf);
    Registry<ConnectionSocketFactory> registry = RegistryBuilder
      .<ConnectionSocketFactory> create()
      .register("https", cf)
      .register("http", PlainConnectionSocketFactory.INSTANCE)
      .build();
    clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager(registry));
  };

  /**
   * @deprecated use {@link #SNIHttpClientConnectionFactory(Supplier)} instead.
   */
  @Deprecated
  public SNIHttpClientConnectionFactory() {
    this(() -> null);
  }

  public SNIHttpClientConnectionFactory(@NotNull Supplier<KeyStore> trustStoreGetter) {
    myTrustStoreGetter = trustStoreGetter;
  }

  public HttpConnection create(final URL url) throws IOException {
    SSLHttpClientConnection connection = new SSLHttpClientConnection(url.toString(), mySSLSchemePatcher);
    connection.setTrustStoreGetter(myTrustStoreGetter);
    return connection;
  }

  public HttpConnection create(final URL url, final Proxy proxy) throws IOException {
    SSLHttpClientConnection connection = new SSLHttpClientConnection(url.toString(), proxy, mySSLSchemePatcher);
    connection.setTrustStoreGetter(myTrustStoreGetter);
    return connection;
  }
}
