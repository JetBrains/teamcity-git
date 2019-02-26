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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.ssl.SSLContextUtil;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.TemporaryBufferEntity;
import org.eclipse.jgit.transport.http.apache.internal.HttpApacheText;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.net.ProtocolException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Same as org.eclipse.jgit.transport.http.apache.HttpClientConnection, but
 * always uses a custom SSLSocketFactory which enables SNI
 */
public class SNIHttpClientConnection implements HttpConnection {
  private static Logger LOG = Logger.getInstance(SNIHttpClientConnection.class.getName());

  CloseableHttpClient client;

  String urlStr;

  HttpUriRequest req;

  CloseableHttpResponse resp = null;

  String method = "GET"; //$NON-NLS-1$

  private TemporaryBufferEntity entity;

  private boolean isUsingProxy = false;

  private Proxy proxy;

  private Integer timeout = null;

  private Integer readTimeout;

  private Boolean followRedirects;

  private X509HostnameVerifier hostnameverifier;

  SSLContext ctx;

  private Supplier<KeyStore> trustStoreGetter = () -> null;

  private Map<String, Object> attributes = new HashMap<String, Object>();

  private Function<X509HostnameVerifier, Collection<Scheme>> myAdditionalSchemesProvider;

  private CloseableHttpClient getClient() {
    if (client == null)
      client = new DefaultHttpClient();
    HttpParams params = client.getParams();
    if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
      isUsingProxy = true;
      InetSocketAddress adr = (InetSocketAddress) proxy.address();
      params.setParameter(ConnRoutePNames.DEFAULT_PROXY,
                          new HttpHost(adr.getHostName(), adr.getPort()));
    }
    if (timeout != null)
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
                             timeout);
    if (readTimeout != null)
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,
                             readTimeout);
    if (followRedirects != null)
      params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS,
                                 followRedirects);

    final Collection<Scheme> schemes = myAdditionalSchemesProvider.apply(hostnameverifier);
    for (Scheme scheme : schemes) {
      client.getConnectionManager().getSchemeRegistry().register(scheme);
    }

    return client;
  }

  private SSLContext getSSLContext() {
    KeyStore trustStore = trustStoreGetter.get();
    if (trustStore != null) {
      ctx = SSLContextUtil.createUserSSLContext(trustStore);
    }
    if (ctx == null) {
      try {
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
        ctx.init(null, null, null);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new IllegalStateException(HttpApacheText.get().unexpectedSSLContextException, e);
      }
    }
    return ctx;
  }

  /**
   * Sets the buffer from which to take the request body
   *
   * @param buffer
   */
  public void setBuffer(TemporaryBuffer buffer) {
    this.entity = new TemporaryBufferEntity(buffer);
  }

  /**
   * @param urlStr
   */
  public SNIHttpClientConnection(String urlStr) {
    this(urlStr, null, null);
  }

  /**
   * @param urlStr
   * @param proxy
   */
  public SNIHttpClientConnection(String urlStr, Proxy proxy) {
    this(urlStr, proxy, null);
  }

  /**
   * @param urlStr
   * @param proxy
   * @param cl
   */
  public SNIHttpClientConnection(String urlStr, Proxy proxy, CloseableHttpClient cl) {
    this.client = cl;
    this.urlStr = urlStr;
    this.proxy = proxy;
    myAdditionalSchemesProvider = (hostnameVerifier) -> {
      SSLSocketFactory sf = hostnameVerifier != null ?
                            new SNISSLSocketFactory(getSSLContext(), hostnameVerifier) :
                            new SNISSLSocketFactory(getSSLContext());
      return Collections.singleton(new Scheme("https", 443, sf));
    };
  }

  public int getResponseCode() throws IOException {
    execute();
    return resp.getStatusLine().getStatusCode();
  }

  public URL getURL() {
    try {
      return new URL(urlStr);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  public String getResponseMessage() throws IOException {
    execute();
    return resp.getStatusLine().getReasonPhrase();
  }

  private void execute() throws IOException, ClientProtocolException {
    if (resp == null)
      if (entity != null) {
        if (req instanceof HttpEntityEnclosingRequest) {
          HttpEntityEnclosingRequest eReq = (HttpEntityEnclosingRequest) req;
          eReq.setEntity(entity);
        }
        resp = getClient().execute(req, new ConnectionHttpContext());
        entity.getBuffer().close();
        entity = null;
      } else
        resp = getClient().execute(req, new ConnectionHttpContext());
  }

  public Map<String, List<String>> getHeaderFields() {
    Map<String, List<String>> ret = new HashMap<String, List<String>>();
    for (Header hdr : resp.getAllHeaders()) {
      List<String> list = ret.computeIfAbsent(hdr.getName(), k -> new LinkedList<String>());
      list.add(hdr.getValue());
    }
    return ret;
  }

  public void setRequestProperty(String name, String value) {
    req.addHeader(name, value);
  }

  public void setRequestMethod(String method) throws ProtocolException {
    this.method = method;
    if ("GET".equalsIgnoreCase(method)) //$NON-NLS-1$
      req = new HttpGet(urlStr);
    else if ("PUT".equalsIgnoreCase(method)) //$NON-NLS-1$
      req = new HttpPut(urlStr);
    else if ("POST".equalsIgnoreCase(method)) //$NON-NLS-1$
      req = new HttpPost(urlStr);
    else {
      this.method = null;
      throw new UnsupportedOperationException();
    }
  }

  public void setUseCaches(boolean usecaches) {
    // not needed
  }

  public void setConnectTimeout(int timeout) {
    this.timeout = new Integer(timeout);
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = new Integer(readTimeout);
  }

  public String getContentType() {
    HttpEntity responseEntity = resp.getEntity();
    if (responseEntity != null) {
      Header contentType = responseEntity.getContentType();
      if (contentType != null)
        return contentType.getValue();
    }
    return null;
  }

  public InputStream getInputStream() throws IOException {
    final InputStream delegate = resp.getEntity().getContent();
    return new InputStream() {
      public int read() throws IOException {
        return delegate.read();
      }

      public int read(final byte[] b) throws IOException {
        return delegate.read(b);
      }

      public int read(final byte[] b, final int off, final int len) throws IOException {
        return delegate.read(b, off, len);
      }

      public long skip(final long n) throws IOException {
        return delegate.skip(n);
      }

      public int available() throws IOException {
        return delegate.available();
      }

      public void close() throws IOException {
        try {
          delegate.close();
        } finally {
          try {
            resp.close();
          } finally {
            client.close();
            client = null;
          }
        }
      }

      public void mark(final int readlimit) {
        delegate.mark(readlimit);
      }

      public void reset() throws IOException {
        delegate.reset();
      }

      public boolean markSupported() {
        return delegate.markSupported();
      }
    };
  }

  // will return only the first field
  public String getHeaderField(String name) {
    Header header = resp.getFirstHeader(name);
    return (header == null) ? null : header.getValue();
  }

  public int getContentLength() {
    return Integer.parseInt(resp.getFirstHeader("content-length") //$NON-NLS-1$
                              .getValue());
  }

  public void setInstanceFollowRedirects(boolean followRedirects) {
    this.followRedirects = new Boolean(followRedirects);
  }

  public void setDoOutput(boolean dooutput) {
    // TODO: check whether we can really ignore this.
  }

  public void setFixedLengthStreamingMode(int contentLength) {
    if (entity != null)
      throw new IllegalArgumentException();
    entity = new TemporaryBufferEntity(new LocalFile(null));
    entity.setContentLength(contentLength);
  }

  public OutputStream getOutputStream() throws IOException {
    if (entity == null)
      entity = new TemporaryBufferEntity(new LocalFile(null));
    return entity.getBuffer();
  }

  public void setChunkedStreamingMode(int chunklen) {
    if (entity == null)
      entity = new TemporaryBufferEntity(new LocalFile(null));
    entity.setChunked(true);
  }

  public String getRequestMethod() {
    return method;
  }

  public boolean usingProxy() {
    return isUsingProxy;
  }

  public void connect() throws IOException {
    execute();
  }

  public void setHostnameVerifier(final HostnameVerifier hostnameverifier) {
    this.hostnameverifier = new X509HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return hostnameverifier.verify(hostname, session);
      }

      public void verify(String host, String[] cns, String[] subjectAlts)
        throws SSLException {
        throw new UnsupportedOperationException(); // TODO message
      }

      public void verify(String host, X509Certificate cert)
        throws SSLException {
        throw new UnsupportedOperationException(); // TODO message
      }

      public void verify(String host, SSLSocket ssl) throws IOException {
        hostnameverifier.verify(host, ssl.getSession());
      }
    };
  }

  public void configure(KeyManager[] km, TrustManager[] tm,
                        SecureRandom random) throws KeyManagementException {
    getSSLContext().init(km, tm, random);
  }

  public synchronized void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  public void setTrustStoreGetter(@NotNull final Supplier<KeyStore> trustStoreGetter) {
    this.trustStoreGetter = trustStoreGetter;
  }

  private class ConnectionHttpContext implements HttpContext {
    public synchronized Object getAttribute(String s) {
      return attributes.get(s);
    }

    public synchronized void setAttribute(String s, Object o) {
      attributes.put(s, o);
    }

    public synchronized Object removeAttribute(String s) {
      return attributes.remove(s);
    }
  }


}
