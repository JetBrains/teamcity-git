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
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;
import org.eclipse.jgit.transport.http.apache.TemporaryBufferEntity;
import org.eclipse.jgit.transport.http.apache.internal.HttpApacheText;
import org.eclipse.jgit.util.TemporaryBuffer;
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
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link HttpClientConnection} with support of custom ssl trust store.
 *
 * It is almost copy-paste of {@link HttpClientConnection}.
 *
 * @author Mikhail Khorkov
 * @since 2018.1
 */
public class SSLHttpClientConnection extends HttpClientConnection {

  HttpClient client;

  String urlStr;

  HttpUriRequest req;

  HttpResponse resp = null;

  String method = "GET"; //$NON-NLS-1$

  private TemporaryBufferEntity entity;

  private boolean isUsingProxy = false;

  private Proxy proxy;

  private Integer timeout = null;

  private Integer readTimeout;

  private Boolean followRedirects;

  private X509HostnameVerifier hostnameverifier;

  SSLContext ctx;

  private Map<String, Object> attributes = new HashMap<>();

  @NotNull
  private Supplier<KeyStore> myTrustStoreGetter = () -> null;

  public SSLHttpClientConnection(final String urlStr) {
    super(urlStr);
  }

  public SSLHttpClientConnection(final String urlStr, final Proxy proxy) {
    super(urlStr, proxy);
  }

  public SSLHttpClientConnection(final String urlStr, final Proxy proxy, final HttpClient cl) {
    super(urlStr, proxy, cl);
  }

  private HttpClient getClient() {
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
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
    if (readTimeout != null)
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, readTimeout);
    if (followRedirects != null)
      params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, followRedirects);
    if (hostnameverifier != null) {
      SSLSocketFactory sf;
      sf = new SSLSocketFactory(getSSLContext(), hostnameverifier);
      Scheme https = new Scheme("https", 443, sf); //$NON-NLS-1$
      client.getConnectionManager().getSchemeRegistry().register(https);
    }

    return client;
  }

  private SSLContext getSSLContext() {
    final KeyStore trustStore = myTrustStoreGetter.get();
    if (trustStore != null) {
      ctx = SSLContextUtil.createUserSSLContext(trustStore);
    }
    if (ctx == null) {
      try {
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(
          HttpApacheText.get().unexpectedSSLContextException, e);
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
        resp = getClient().execute(req, new SSLHttpClientConnection.ConnectionHttpContext());
        entity.getBuffer().close();
        entity = null;
      } else
        resp = getClient().execute(req, new SSLHttpClientConnection.ConnectionHttpContext());
  }

  public Map<String, List<String>> getHeaderFields() {
    Map<String, List<String>> ret = new HashMap<>();
    for (Header hdr : resp.getAllHeaders()) {
      List<String> list = ret.computeIfAbsent(hdr.getName(), k -> new LinkedList<>());
      list.add(hdr.getValue());
    }
    return ret;
  }

  public void setRequestProperty(String name, String value) {
    req.addHeader(name, value);
  }

  public void setRequestMethod(String method) {
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
    this.timeout = timeout;
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
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
    return resp.getEntity().getContent();
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
    this.followRedirects = followRedirects;
  }

  public void setDoOutput(boolean dooutput) {
    // TODO: check whether we can really ignore this.
  }

  public void setFixedLengthStreamingMode(int contentLength) {
    if (entity != null)
      throw new IllegalArgumentException();
    entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
    entity.setContentLength(contentLength);
  }

  public OutputStream getOutputStream() {
    if (entity == null)
      entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
    return entity.getBuffer();
  }

  public void setChunkedStreamingMode(int chunklen) {
    if (entity == null)
      entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
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

      public void verify(String host, String[] cns, String[] subjectAlts) {
        throw new UnsupportedOperationException(); // TODO message
      }

      public void verify(String host, X509Certificate cert) {
        throw new UnsupportedOperationException(); // TODO message
      }

      public void verify(String host, SSLSocket ssl) {
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

  public void setTrustStoreGetter(@NotNull final Supplier<KeyStore> trustStoreGetter) {
    myTrustStoreGetter = trustStoreGetter;
  }
}
