package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SNISSLSocketFactory extends SSLSocketFactory {
  private static Logger LOG = Logger.getInstance(SSLHttpClientConnection.class.getName());

  public SNISSLSocketFactory(final SSLContext sslContext) {
    super(sslContext);
  }

  public SNISSLSocketFactory(final SSLContext sslContext, final X509HostnameVerifier hostnameVerifier) {
    super(sslContext, hostnameVerifier);
  }

  @Override
  public Socket connectSocket(final int connectTimeout,
                              final Socket socket,
                              final HttpHost host,
                              final InetSocketAddress remoteAddress,
                              final InetSocketAddress localAddress,
                              final HttpContext context) throws IOException {
    if (socket instanceof SSLSocket) {
      enableSNI((SSLSocket)socket, host);
    }
    return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
  }

  private void enableSNI(SSLSocket socket, final HttpHost host) {
    String sniEnabled = System.getProperty("jsse.enableSNIExtension");
    if (StringUtil.isEmpty(sniEnabled) || Boolean.parseBoolean(sniEnabled)) {
      try {
        Method method = socket.getClass().getDeclaredMethod("setHost", String.class);
        method.invoke(socket, host.getHostName());
      } catch (Exception e) {
        LOG.info("Cannot enable SNI for host " + host.getHostName() + ", continue without SNI, error: " + e.toString());
      }
    }
  }
}
