package jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.jetbrains.annotations.NotNull;
import sun.net.spi.DefaultProxySelector;

public class SSLConnectionCheck {

  public static void tryConnect(@NotNull SSLContext sslContext, @NotNull String destHost, int destPort) throws Exception {
    List<String> systemParamNames = Arrays.asList("https.proxyHost", "https.proxyPort", "http.nonProxyHosts");

    // set system properties for DefaultProxySelector, using teamcity.https.proxyHost, etc.
    for (String param : systemParamNames) {
      String val = TeamCityProperties.getPropertyOrNull("teamcity." + param);
      if (val != null) {
        System.setProperty(param, val);
      }
    }
    String httpsProxyHosts = TeamCityProperties.getPropertyOrNull("teamcity.https.nonProxyHosts");
    if (httpsProxyHosts != null) {
      System.setProperty("http.nonProxyHosts", httpsProxyHosts);
    }
    DefaultProxySelector proxySelector = new DefaultProxySelector();

    //get suitable proxy and check if the host is in nonProxyHosts
    Proxy proxyConfig = proxySelector.select(new URI("https", null, destHost, destPort, null, null, null)).get(0);

    switch (proxyConfig.type()) {
      case DIRECT:
        try(SSLSocket socket = setUpDirectSSLSocket(sslContext, destHost, destPort)) {
          socket.setSoTimeout(getConnectionTimeout());
          socket.startHandshake();
        }
        break;
      case SOCKS:
        throw new UnexpectedErrorException("SOCKS proxy is not supported");
      case HTTP:
        InetSocketAddress proxyAddr = (InetSocketAddress)proxyConfig.address();
        final String proxyScheme = TeamCityProperties.getProperty("teamcity.https.proxyScheme", "http");
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                                                      .setSSLContext(sslContext)
                                                      .setProxy(new HttpHost(proxyAddr.getHostName(), proxyAddr.getPort(), proxyScheme));

        int connectionTimeout = getConnectionTimeout();
        clientBuilder.setDefaultRequestConfig(RequestConfig.copy(RequestConfig.DEFAULT)
                                                               .setConnectionRequestTimeout(connectionTimeout)
                                                               .setConnectTimeout(connectionTimeout).build());
        SocketConfig.Builder socketConfig = SocketConfig.copy(SocketConfig.DEFAULT);
        socketConfig.setSoTimeout(connectionTimeout);
        clientBuilder.setDefaultSocketConfig(socketConfig.build());

        final String username = TeamCityProperties.getPropertyOrNull("teamcity.https.proxyLogin");
        final String password = TeamCityProperties.getPropertyOrNull("teamcity.https.proxyPassword");
        final String auth = TeamCityProperties.getPropertyOrNull("teamcity.https.proxyAuthentication");
        final String authType = TeamCityProperties.getProperty("teamcity.https.proxyAuthenticationType", "basic");

        Credentials credentials = null;
        // other authentication types can be supported,
        // but currently authentication is handled the same way as in HttpClientConfigurator
        switch (authType.toLowerCase()) {
          case "ntlm":
            if (auth != null) {
              credentials = new NTCredentials(auth);
            }
            break;
          default:
            if (auth != null) {
              credentials = new UsernamePasswordCredentials(auth);
            } else if (username != null) {
              credentials = new UsernamePasswordCredentials(username, password);
            }
            break;
        }

        if (credentials != null) {
          SystemDefaultCredentialsProvider credentialsProvider = new SystemDefaultCredentialsProvider();
          credentialsProvider.setCredentials(new AuthScope(proxyAddr.getHostName(), proxyAddr.getPort(), null, authType.toUpperCase()), credentials);
          clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        try(CloseableHttpClient client = clientBuilder.build()) {
          CloseableHttpResponse response = client.execute(new HttpGet(new URI("https", null, destHost, destPort, null, null, null)));
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new UnexpectedErrorException(String.format("Got error response(code %d) from %s while checking ssl connection: %s)",
                                                             response.getStatusLine().getStatusCode(), destHost, response.getStatusLine().getReasonPhrase()));
          }
        }

        break;
    }
  }

  public static class UnexpectedErrorException extends Exception {
    UnexpectedErrorException(@NotNull String message) {
      super(message);
    }
  }

  private static int getConnectionTimeout() {
    return TeamCityProperties.getInteger("teamcity.ssl.checkTimeout.git", 10 * 1000);
  }

  @NotNull
  private static SSLSocket setUpDirectSSLSocket(@NotNull SSLContext sslContext, @NotNull String host, @NotNull Integer port) throws IOException {
    return (SSLSocket)sslContext.getSocketFactory().createSocket(host, port);
  }
}
