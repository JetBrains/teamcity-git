

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

/**
 * {@link HttpConnectionFactory} with support of custom ssl trust store.
 *
 * Returns instances of {@link SSLHttpClientConnection}.
 *
 * @author Mikhail Khorkov
 * @since 2018.1
 */
public class SSLHttpClientConnectionFactory implements HttpConnectionFactory {

  @NotNull
  private final Supplier<KeyStore> myTrustStoreGetter;
  private final SSLSchemePatcher mySSLSchemePatcher = (clientBuilder, sslContext, hostnameVerifier) -> {
    if (hostnameVerifier != null) {
      SSLConnectionSocketFactory cf = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
      clientBuilder.setSSLSocketFactory(cf);
      Registry<ConnectionSocketFactory> registry = RegistryBuilder
        .<ConnectionSocketFactory> create()
        .register("https", cf)
        .register("http", PlainConnectionSocketFactory.INSTANCE)
        .build();
      clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager(registry));
    }
  };

  public SSLHttpClientConnectionFactory(@NotNull final Supplier<KeyStore> trustStoreGetter) {
    myTrustStoreGetter = trustStoreGetter;
  }

  @Override
  public HttpConnection create(final URL url) throws IOException {
    SSLHttpClientConnection connection = new SSLHttpClientConnection(url.toString(), mySSLSchemePatcher);
    connection.setTrustStoreGetter(myTrustStoreGetter);
    return connection;
  }

  @Override
  public HttpConnection create(final URL url, final Proxy proxy) throws IOException {
    SSLHttpClientConnection connection = new SSLHttpClientConnection(url.toString(), proxy, mySSLSchemePatcher);
    connection.setTrustStoreGetter(myTrustStoreGetter);
    return connection;
  }
}