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

import jetbrains.buildServer.util.ssl.SSLContextUtil;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Basically a copy of org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
 * which gives ability to change used SSL protocol.
 */
public class TeamCityJDKHttpConnectionFactory implements HttpConnectionFactory {

  private final ServerPluginConfig myConfig;
  private Supplier<KeyStore> myTrustStoreGetter;

  public TeamCityJDKHttpConnectionFactory(@NotNull ServerPluginConfig config, @NotNull Supplier<KeyStore> trustStoreGetter) {
    myConfig = config;
    myTrustStoreGetter = trustStoreGetter;
  }

  public HttpConnection create(URL url) throws IOException {
    return new TeamCityJDKHttpConnection(url);
  }

  public HttpConnection create(URL url, Proxy proxy) throws IOException {
    return new TeamCityJDKHttpConnection(url, proxy);
  }


  private class TeamCityJDKHttpConnection implements HttpConnection {
    private HttpURLConnection wrappedUrlConnection;

    protected TeamCityJDKHttpConnection(URL url) throws MalformedURLException, IOException {
      this.wrappedUrlConnection = (HttpURLConnection) url.openConnection();
      if ("https".equals(url.getProtocol())) {
        workaroundSslDeadlock();
      }
    }

    protected TeamCityJDKHttpConnection(URL url, Proxy proxy) throws MalformedURLException, IOException {
      this.wrappedUrlConnection = (HttpURLConnection) url.openConnection(proxy);
      if ("https".equals(url.getProtocol())) {
        workaroundSslDeadlock();
      }
    }

    public int getResponseCode() throws IOException {
      return wrappedUrlConnection.getResponseCode();
    }

    public URL getURL() {
      return wrappedUrlConnection.getURL();
    }

    public String getResponseMessage() throws IOException {
      return wrappedUrlConnection.getResponseMessage();
    }

    public Map<String, List<String>> getHeaderFields() {
      return wrappedUrlConnection.getHeaderFields();
    }

    public void setRequestProperty(String key, String value) {
      wrappedUrlConnection.setRequestProperty(key, value);
    }

    public void setRequestMethod(String method) throws ProtocolException {
      wrappedUrlConnection.setRequestMethod(method);
    }

    public void setUseCaches(boolean usecaches) {
      wrappedUrlConnection.setUseCaches(usecaches);
    }

    public void setConnectTimeout(int timeout) {
      wrappedUrlConnection.setConnectTimeout(timeout);
    }

    public void setReadTimeout(int timeout) {
      wrappedUrlConnection.setReadTimeout(timeout);
    }

    public String getContentType() {
      return wrappedUrlConnection.getContentType();
    }

    public InputStream getInputStream() throws IOException {
      return wrappedUrlConnection.getInputStream();
    }

    public String getHeaderField(String name) {
      return wrappedUrlConnection.getHeaderField(name);
    }

    public int getContentLength() {
      return wrappedUrlConnection.getContentLength();
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
      wrappedUrlConnection.setInstanceFollowRedirects(followRedirects);
    }

    public void setDoOutput(boolean dooutput) {
      wrappedUrlConnection.setDoOutput(dooutput);
    }

    public void setFixedLengthStreamingMode(int contentLength) {
      wrappedUrlConnection.setFixedLengthStreamingMode(contentLength);
    }

    public OutputStream getOutputStream() throws IOException {
      return wrappedUrlConnection.getOutputStream();
    }

    public void setChunkedStreamingMode(int chunklen) {
      wrappedUrlConnection.setChunkedStreamingMode(chunklen);
    }

    public String getRequestMethod() {
      return wrappedUrlConnection.getRequestMethod();
    }

    public boolean usingProxy() {
      return wrappedUrlConnection.usingProxy();
    }

    public void connect() throws IOException {
      wrappedUrlConnection.connect();
    }

    public void setHostnameVerifier(HostnameVerifier hostnameverifier) {
      ((HttpsURLConnection) wrappedUrlConnection).setHostnameVerifier(hostnameverifier);
    }

    public void configure(KeyManager[] km, TrustManager[] tm, SecureRandom random) throws NoSuchAlgorithmException, KeyManagementException {
      SSLContext ctx = SSLContext.getInstance(myConfig.getHttpConnectionSslProtocol()); //$NON-NLS-1$
      ctx.init(km, tm, random);
      ((HttpsURLConnection) wrappedUrlConnection).setSSLSocketFactory(createSSLSocketFactory(ctx.getSocketFactory()));
    }

    private void workaroundSslDeadlock() throws IOException {
      ((HttpsURLConnection) wrappedUrlConnection).setSSLSocketFactory(createSSLSocketFactory());
    }

    private SSLSocketFactory createSSLSocketFactory() {
      final SSLContext trusted = SSLContextUtil.createUserSSLContext(myTrustStoreGetter.get());
      final SSLSocketFactory origin;
      if (trusted != null) {
        origin = trusted.getSocketFactory();
      } else {
        origin = ((HttpsURLConnection) wrappedUrlConnection).getSSLSocketFactory();
      }
      return createSSLSocketFactory(origin);
    }

    private SSLSocketFactory createSSLSocketFactory(@NotNull SSLSocketFactory origin) {
      return new SSLSocketFactoryWithSoLinger(origin, myConfig.getHttpsSoLinger());
    }

    public void setAttribute(String name, Object value) {
    }
  }

  private static class SSLSocketFactoryWithSoLinger extends SSLSocketFactory {

    private final SSLSocketFactory delegate;
    private final int mySoLinger;

    private SSLSocketFactoryWithSoLinger(SSLSocketFactory delegate, int soLinger) {
      this.delegate = delegate;
      mySoLinger = soLinger;
    }

    @Override
    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
      final Socket socket = delegate.createSocket(s, host, port, autoClose);
      setSoLinger(socket);
      return socket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
      final Socket socket = delegate.createSocket(host, port);
      setSoLinger(socket);
      return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
      final Socket socket = delegate.createSocket(host, port, localHost, localPort);
      setSoLinger(socket);
      return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      final Socket socket = delegate.createSocket(host, port);
      setSoLinger(socket);
      return socket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      final Socket socket = delegate.createSocket(address, port, localAddress, localPort);
      setSoLinger(socket);
      return socket;
    }

    private void setSoLinger(Socket s) throws SocketException {
      if (mySoLinger >= 0)
        s.setSoLinger(true, mySoLinger);
    }
  }
}
