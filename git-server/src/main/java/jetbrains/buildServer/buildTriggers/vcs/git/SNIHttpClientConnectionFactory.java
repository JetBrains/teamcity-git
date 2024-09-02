

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